#!/bin/bash

set -e # fail script on any individual command failing
shopt -s nullglob

export LANG=en_US.UTF-8

STACK_NAME=$1
S3_BUCKET=$2
SOURCE_STREAM_ARN=$3
DEST_TABLE_NAME=$4
TRANSFORM_CLASS=$5

if [ -z "$STACK_NAME" ]; then
  echo "usage deploy.sh <stack_name> <s3_bucket> <source_stream_arn> <dest_table_name> [transform_class]"
  exit 1
fi

if [ -z "$S3_BUCKET" ]; then
  echo "usage deploy.sh <stack_name> <s3_bucket> <source_stream_arn> <dest_table_name> [transform_class]"
  exit 1
fi

if [ -z "$SOURCE_STREAM_ARN" ]; then
  echo "usage deploy.sh <stack_name> <s3_bucket> <source_stream_arn> <dest_table_name> [transform_class]"
  exit 1
fi

if [ -z "$DEST_TABLE_NAME" ]; then
  echo "usage deploy.sh <stack_name> <s3_bucket> <source_stream_arn> <dest_table_name> [transform_class]"
  exit 1
fi

echo Building app...
mvn clean package

echo "Packaging app template"
output_template_path=target/package_template.yml
aws cloudformation package \
  --template-file template.yml \
  --output-template-file $output_template_path \
  --s3-bucket $S3_BUCKET

echo "Deploying app stack: ${STACK_NAME}"
aws cloudformation deploy \
  --template-file $output_template_path \
  --stack-name ${STACK_NAME} \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides SourceTableStreamARN="$SOURCE_STREAM_ARN" DestinationTableName="$DEST_TABLE_NAME" TransformClass="$TRANSFORM_CLASS"
