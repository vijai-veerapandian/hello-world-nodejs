module.exports = {
    testEnvironment: 'node',
    collectCoverage: true,
    coverageReporters: ['text', 'lcov', 'clover', 'html'],
    reporters: [
        'default',
        ['jest-junit', { outputDirectory: '.', outputName: 'test-results.xml' }]
    ],
    testMatch: [
        '**/__tests__/**/*.js',
        '**/?(*.)+(spec|test).js'

    ]
};