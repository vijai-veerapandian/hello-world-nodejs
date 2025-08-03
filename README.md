# Hello World Node.js Application

A simple Hello World application built with Node.js and Express, featuring comprehensive test coverage.

## Features

- Simple HTTP server using Express.js
- Health check endpoint
- Comprehensive test suite with Jest
- Development server with auto-reload using Nodemon

## Installation

```bash
npm install
```

## Usage

### Start the server:
```bash
npm start
```

### Development mode (with auto-reload):
```bash
npm run dev
```

### Run tests:
```bash
npm test
```

### Run tests in watch mode:
```bash
npm run test:watch
```

## Endpoints

- `GET /` - Returns "Hello, World!"
- `GET /api/health` - Returns server health status

## Testing

The application includes comprehensive tests using Jest and Supertest:
- Route testing
- Response validation
- Error handling
- Health check endpoint testing

Run `npm test` to execute all tests and generate coverage reports.

### Docker/Containzerization

Now, the application can do deployed as Docker, or into Kubernetes cluster.

## Features

- Simple HTTP server using Express.js
- Health check endpoint
- Comprehensive test suite with Jest
- Development server with auto-reload using Nodemon
- Docker support with multi-stage builds
- Production-ready containerization
- Kubernetes deployment manifests
- CI/CD pipeline configuration

## Installation

### Local Development
```bash
npm install
```

### Docker Development
```bash
docker buildx build -t hello-world-app .
```

## Usage

### Local Development

#### Start the server:
```bash
npm start
```

#### Development mode (with auto-reload):
```bash
npm run dev
```

#### Run tests:
```bash
npm test
```

#### Run tests in watch mode:
```bash
npm run test:watch
```

### Docker Usage

#### Prerequisites:
```bash
# Ensure Docker Buildx is available (comes with Docker Desktop or newer Docker versions)
docker buildx version

# Create and use a new builder instance (optional, for advanced features)
docker buildx create --name mybuilder --use
docker buildx inspect --bootstrap
```

#### Build and run production container:
```bash
# Build using Docker Buildx
docker buildx build -t hello-world-app --load .

# Run the container locally (for testing)
docker run -p 3000:3000 hello-world-app
```

#### Build and push to registry for Kubernetes:
```bash
# Build and push to container registry
docker buildx build -t your-registry/hello-world-app:latest --push .

# Build for multiple platforms and push
docker buildx build --platform linux/amd64,linux/arm64 -t your-registry/hello-world-app:latest --push .
```

#### Build development container (for local testing):
```bash
# Build development image
docker buildx build -f Dockerfile.dev -t hello-world-app:dev --load .

# Run development container
docker run -p 3000:3000 hello-world-app:dev
```

#### Testing with Development Container:
```bash
# Build development container
docker buildx build -f Dockerfile.dev -t hello-world-app:dev --load .

# Run tests in container
docker run --rm hello-world-app:dev npm test

# Check test coverage
docker run --rm hello-world-app:dev npm test -- --coverage --watchAll=false

# Run linting (if configured)
docker run --rm hello-world-app:dev npm run lint
```

### Kubernetes Deployment

After pushing your image to a container registry, deploy to Kubernetes:

```bash
# Apply the Kubernetes manifests
kubectl apply -f k8s-deployment.yaml

# Check deployment status
kubectl get deployments
kubectl get pods
kubectl get services

# Get external IP (if using LoadBalancer)
kubectl get service hello-world-service
```

## Endpoints

- `GET /` - Returns "Hello, World!"
- `GET /api/health` - Returns server health status

## Docker Configuration

The application includes Docker configurations optimized for Kubernetes deployment:

### Production Dockerfile
- Uses Node.js 18 Alpine for smaller image size (~100MB)
- Non-root user for security
- Health checks included
- Only production dependencies installed
- Optimized for container registry and K8s deployment

### Development Dockerfile  
- Includes all dependencies (dev + production) (~200MB)
- Full testing and development toolset
- Used in CI/CD pipelines for testing
- Ensures environment consistency

### Kubernetes Deployment
- Production-ready K8s manifests included
- Horizontal scaling with replicas (3 by default)
- Health checks (liveness and readiness probes)
- Resource limits and requests
- LoadBalancer service for external access

## CI/CD Pipeline

The application includes a complete GitHub Actions workflow:

### Pipeline Stages:
1. **Test Stage** (using Dockerfile.dev):
   - Build development container
   - Run comprehensive tests
   - Check code coverage
   - Run linting and security checks

2. **Build & Deploy Stage** (using Dockerfile):
   - Build optimized production image
   - Push to container registry
   - Deploy to Kubernetes cluster

### Pipeline Benefits:
- **Environment Consistency**: Tests run in same environment as production
- **Security**: Production images contain no development tools
- **Quality Gates**: Production deployment only after tests pass
- **Multi-platform Support**: Builds for AMD64 and ARM64 architectures

## File Structure

```
├── server.js              # Main application file
├── package.json            # NPM configuration and dependencies
├── Dockerfile             # Production container configuration
├── Dockerfile.dev         # Development container configuration
├── .dockerignore          # Docker build exclusions
├── healthcheck.js         # Container health check script
├── k8s-deployment.yaml    # Kubernetes deployment manifests
├── .github/workflows/     # CI/CD pipeline configuration
├── __tests__/             # Test files
│   └── server.test.js     # Application tests
├── jest.config.js         # Jest testing configuration
└── README.md              # This file
```

## Testing

The application includes comprehensive tests using Jest and Supertest:
- Route testing
- Response validation
- Error handling
- Health check endpoint testing

### Local Testing:
```bash
npm test
```

### Container Testing:
```bash
# Build and test in development container
docker buildx build -f Dockerfile.dev -t hello-world-app:dev --load .
docker run --rm hello-world-app:dev npm test
```

## Security Features

- Non-root user in containers
- Minimal Alpine Linux base image
- .dockerignore to exclude sensitive files
- Health checks for container monitoring
- Resource limits in Kubernetes
- Separation of development and production dependencies

## Development Workflow

### Local Development:
1. `npm install` - Install dependencies
2. `npm run dev` - Start development server
3. `npm test` - Run tests
4. Make changes and test locally

### Container Development:
1. `docker buildx build -f Dockerfile.dev -t hello-world-app:dev --load .` - Build dev container
2. `docker run --rm hello-world-app:dev npm test` - Test in container
3. `docker run -p 3000:3000 hello-world-app:dev` - Run dev server in container

### Production Deployment:
1. Push code to repository
2. CI/CD pipeline automatically:
   - Builds development container
   - Runs all tests
   - Builds production container (if tests pass)
   - Pushes to registry
   - Deploys to Kubernetes

## Environment Variables

- `NODE_ENV` - Environment (development/production)
- `PORT` - Server port (default: 3000)

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests: `npm test`
5. Test in container: `docker run --rm hello-world-app:dev npm test`
6. Submit a pull request

## License

MIT