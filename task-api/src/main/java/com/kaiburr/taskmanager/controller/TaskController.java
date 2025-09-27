package com.kaiburr.taskmanager.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kaiburr.taskmanager.model.Task;
import com.kaiburr.taskmanager.model.TaskExecution;
import com.kaiburr.taskmanager.repository.TaskRepository;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    @Autowired
    private TaskRepository taskRepository;

    // GET all tasks or by ID
    @GetMapping
    public ResponseEntity<?> getTasks(@RequestParam(required = false) String id) {
        if (id == null) {
            return ResponseEntity.ok(taskRepository.findAll());
        }
        return taskRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("Task not found"));
    }

    // PUT (create/update) a task
    @PutMapping
    public Task putTask(@RequestBody Task task) {
        // Basic command validation
        if (task.getCommand() != null) {
            String cmd = task.getCommand().toLowerCase();
            if (cmd.contains("rm ") || cmd.contains("del ") || cmd.contains("format") || 
                cmd.contains("shutdown") || cmd.contains("reboot")) {
                throw new RuntimeException("Unsafe command detected!");
            }
        }
        
        // Initialize taskExecutions if null
        if (task.getTaskExecutions() == null) {
            task.setTaskExecutions(new ArrayList<>());
        }
        
        return taskRepository.save(task);
    }

    // DELETE task by ID
    @DeleteMapping("/{id}")
    public String deleteTask(@PathVariable String id) {
        taskRepository.deleteById(id);
        return "Deleted task " + id;
    }

    // GET tasks by name
    @GetMapping("/search")
    public ResponseEntity<?> searchByName(@RequestParam String name) {
        List<Task> tasks = taskRepository.findByNameContainingIgnoreCase(name);
        if (tasks.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No tasks found");
        }
        return ResponseEntity.ok(tasks);
    }

    // PUT TaskExecution (run command)
    @PutMapping("/{id}/execute")
    public Task executeTask(@PathVariable String id) {
        Task task = taskRepository.findById(id).orElseThrow(() -> new RuntimeException("Task not found"));

        try {
            Date start = new Date();
            String mode = System.getenv("EXECUTION_MODE");
            boolean useK8s = "k8s".equalsIgnoreCase(mode);
            String commandOutput;

            if (useK8s) {
                commandOutput = executeInKubernetes(task.getCommand());
            } else {
                // Cross-platform shell execution: use cmd.exe on Windows and /bin/sh on Unix
                boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
                String[] cmd = isWindows
                        ? new String[] {"cmd.exe", "/c", task.getCommand()}
                        : new String[] {"/bin/sh", "-c", task.getCommand()};

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true); // merge stderr into stdout
                Process process = pb.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                process.waitFor();
                commandOutput = output.toString();
            }

            Date end = new Date();

            // Ensure taskExecutions list is initialized
            if (task.getTaskExecutions() == null) {
                task.setTaskExecutions(new ArrayList<>());
            }

            TaskExecution execution = new TaskExecution(start, end, commandOutput);
            task.getTaskExecutions().add(execution);

            return taskRepository.save(task);
        } catch (IOException e) {
            throw new RuntimeException("Error during command execution: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Command execution was interrupted: " + e.getMessage());
        }
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeException(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    // Execute a shell command inside a Kubernetes pod (busybox)
    private String executeInKubernetes(String command) {
        String namespace = System.getenv().getOrDefault("K8S_NAMESPACE", "default");
        String podName = "task-exec-" + UUID.randomUUID().toString().substring(0, 8);

        try (KubernetesClient client = new DefaultKubernetesClient()) {
            Pod pod = new PodBuilder()
                .withNewMetadata()
                    .withName(podName)
                    .addToLabels("app", "task-exec")
                .endMetadata()
                .withNewSpec()
                    .withRestartPolicy("Never")
                    .addNewContainer()
                        .withName("runner")
                        .withImage("busybox:1.36")
                        .withCommand("/bin/sh", "-c", command)
                    .endContainer()
                .endSpec()
                .build();

            client.pods().inNamespace(namespace).resource(pod).create();

            // Wait up to 5 minutes until the Pod completes (Succeeded or Failed)
            client.pods().inNamespace(namespace).withName(podName)
                .waitUntilCondition(p -> {
                    if (p == null || p.getStatus() == null || p.getStatus().getPhase() == null) return false;
                    String phase = p.getStatus().getPhase();
                    return "Succeeded".equals(phase) || "Failed".equals(phase);
                }, 5, TimeUnit.MINUTES);

            String logs = client.pods().inNamespace(namespace).withName(podName).getLog(true);
            // Clean up the pod after execution
            client.pods().inNamespace(namespace).withName(podName).delete();

            return logs == null ? "" : logs;
        } catch (Exception ex) {
            throw new RuntimeException("Kubernetes execution failed: " + ex.getMessage());
        }
    }
}