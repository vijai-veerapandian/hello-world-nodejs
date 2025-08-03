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