// __tests__/server.test.js
const request = require('supertest');
const app = require('../server');

describe('Hello World App', () => {
    describe('GET /', () => {
        it('should return the static HTML page with status 200', async () => {
            const response = await request(app)
                .get('/')
                .expect(200)
                .expect('Content-Type', /html/);

            expect(response.text).toContain('<h1>Hello, World! version1</h1>');
        });
    });

    describe('GET /api/health', () => {
        it('should return health status with status 200', async () => {
            const response = await request(app)
                .get('/api/health')
                .expect(200)
                .expect('Content-Type', /json/);

            expect(response.body).toMatchObject({
                status: 'OK',
                message: 'Server is running'
            });
            expect(response.body.timestamp).toBeDefined();
        });
    });

    describe('GET /nonexistent', () => {
        it('should return 404 for non-existent routes', async () => {
            await request(app)
                .get('/nonexistent')
                .expect(404);
        });
    });
});