// Simple Hello World Node.js application

// Import the built-in http module
const http = require('http');

// Define the port number
const PORT = 3000;

// Create a simple HTTP server
const server = http.createServer((req, res) => {
    // Set the response header
    res.writeHead(200, { 'Content-Type': 'text/plain' });

    // Send the response
    res.end('Hello, World!');
});

// Start the server
server.listen(PORT, () => {
    console.log(`Server is running on http://localhost:${PORT}`);
});

// Alternative: Simple console output version
// Uncomment the line below for a basic console hello world
// console.log('Hello, World!');