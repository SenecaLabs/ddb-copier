#!/bin/bash

#
# Run during pre-integration-test phase. Deploys integ test environment and test app stack. Assumes running as part of a Travis CI build.
#
# Assumes the following are installed on the build host: AWS CLI, jq
#
# Depends on the following env vars being set:
#  -TRAVIS_BUILD_ID - used to make stack names unique to this build
#  -AWS_DEFAULT_REGION - region in which to run integ tests
#  -AWS_ACCESS_KEY_ID - AWS access key id used for tests
#  -AWS_SECRET_ACCESS_KEY - AWS secret access key used for tests
#  -PACKAGING_S3_BUCKET - S3 bucket used for packaging the app code artifacts
#

set -e # fail script on any individual command failing
shopt -s nullglob

export LANG=en_US.UTF-8

test_environment_stack_name="integ-test-environment-${TRAVIS_BUILD_ID}"
app_stack_name="integ-test-app-${TRAVIS_BUILD_ID}"

echo "Deploying test environment stack: $test_environment_stack_name"
aws cloudformation deploy \
  --template-file src/test/resources/integ-test-environment.yml \
  --stack-name $test_environment_stack_name

test_environment_stack_outputs=$(aws cloudformation describe-stacks --stack-name $test_environment_stack_name | jq -e '.Stacks[0].Outputs')
source_table_name=$(echo $test_environment_stack_outputs | jq -er '.[] | select(.OutputKey | contains("SourceTableName")) | .OutputValue')
source_table_stream_arn=$(echo $test_environment_stack_outputs | jq -er '.[] | select(.OutputKey | contains("SourceTableStreamArn")) | .OutputValue')
copy_table_name=$(echo $test_environment_stack_outputs | jq -er '.[] | select(.OutputKey | contains("CopyTableName")) | .OutputValue')
update_table_name=$(echo $test_environment_stack_outputs | jq -er '.[] | select(.OutputKey | contains("UpdateTableName")) | .OutputValue')

echo "Packaging app template"
output_template_path=target/package_template.yml
aws cloudformation package \
  --template-file template.yml \
  --output-template-file $output_template_path \
  --s3-bucket $PACKAGING_S3_BUCKET

echo "Deploying app stack: ${app_stack_name}-put"
aws cloudformation deploy \
  --template-file $output_template_path \
  --stack-name ${app_stack_name}-put \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides SourceTableStreamARN="$source_table_stream_arn" DestinationTableName="$copy_table_name"

echo "Deploying app stack: ${app_stack_name}-update"
aws cloudformation deploy \
  --template-file $output_template_path \
  --stack-name ${app_stack_name}-update \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides SourceTableStreamARN="$source_table_stream_arn" DestinationTableName="$update_table_name" TransformClass=Update
