
Forked from https://github.com/jlhood/ddb-copier

To run tests:
```bash
export TRAVIS_BUILD_ID=
export AWS_DEFAULT_REGION=
export PACKAGING_S3_BUCKET=
export AWS_ACCESS_KEY_ID=
export AWS_SECRET_ACCESS_KEY=
mvn install

```

#Original Readme

# ddb-copier [![Build Status](https://travis-ci.org/jlhood/ddb-copier.svg?branch=master)](https://travis-ci.org/jlhood/ddb-copier) [![Coverage Status](https://coveralls.io/repos/github/jlhood/ddb-copier/badge.svg?branch=master)](https://coveralls.io/github/jlhood/ddb-copier?branch=master)

Serverless app that copies one DynamoDB table to another by listening on the DDB stream of the source table and copying all changes to the destination table.

Made with ❤️  by James Hood. Available on the [AWS Serverless Application Repository](https://aws.amazon.com/serverless)

## License

MIT License (MIT)
