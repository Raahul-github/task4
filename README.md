# Task 4: CI/CD Pipeline for Task API

This repository contains the `task-api` application, a Java Spring Boot application, and a GitHub Actions workflow for Continuous Integration (CI) and Continuous Deployment (CD).

## Project Structure

*   `task-api/`: The Java Spring Boot application.
*   `.github/workflows/ci-cd.yaml`: The GitHub Actions workflow definition.

## CI/CD Pipeline Overview

The CI/CD pipeline is implemented using GitHub Actions and is triggered on every push to the `main` branch. The workflow performs the following steps:

1.  **Checkout Code**: Fetches the latest code from the repository.
2.  **Set up JDK 17**: Configures the Java Development Kit environment.
3.  **Build with Maven**: Compiles the Java application and runs tests using Apache Maven.
4.  **Login to Docker Hub**: Authenticates with Docker Hub using secrets.
5.  **Build and Push Docker Image**: Builds a Docker image of the `task-api` application and pushes it to Docker Hub.

## How to Use

### 1. GitHub Repository Setup

Ensure this repository is linked to your GitHub account.

### 2. Configure GitHub Secrets

For the Docker login and push steps to work, you need to set up the following secrets in your GitHub repository:

*   `DOCKER_USERNAME`: Your Docker Hub username.
*   `DOCKER_PASSWORD`: Your Docker Hub access token (recommended for security).

To do this:
1.  Go to your GitHub repository (<mcurl name="https://github.com/Raahul-github/task4" url="https://github.com/Raahul-github/task4"></mcurl>).
2.  Click on "Settings".
3.  In the left sidebar, click on "Secrets and variables" > "Actions".
4.  Click on "New repository secret" and add the two secrets mentioned above.

### 3. Triggering the Workflow

The workflow will automatically trigger on any push to the `main` branch. You can also manually trigger it from the "Actions" tab in your GitHub repository.

### 4. Monitoring the Pipeline

You can monitor the progress and view the logs of your CI/CD pipeline by visiting the "Actions" tab in your GitHub repository: <mcurl name="https://github.com/Raahul-github/task4/actions" url="https://github.com/Raahul-github/task4/actions"></mcurl>