# Maven Archetype for lambda function using AWS SDK for Java 2.x

## Description
This is an Apache Maven Archetype to create a lambda function template using [AWS Java SDK 2.x][aws-java-sdk-v2]. The generated template
has the optimized configurations and follows the best practices to reduce start up time.

## Usage

You can use the following command to generate a project:

```
mvn archetype:generate \
  -DarchetypeGroupId=software.amazon.awssdk \
  -DarchetypeArtifactId=lambda-archetypes \
  -DarchetypeVersion=2.x\
```

To deploy the function, you can use [SAM CLI][sam-cli].

```
sam deploy --guided
```
Please refer to [deploying lambda apps][deploying-lambda-apps] for more info.

## Parameters
      
Parameter Name | Default Value | Description
---|---|---
`service` (required) | n/a | Specifies service client to be used in the lambda function. You can find the  eg: s3, dynamodb
`region` (required) | n/a | Specifies region to be set for the SDK client in the application
`groupId`(required) | n/a | Specifies the group ID of the project
`artifactId`(required) | n/a | Specifies the artifact ID of the project
`httpClient` | url-connection-client | Specifies which http client to be used by the SDK client, available options are `url-connection-client`, `apache-client`, `netty-nio-client`
`handlerClassName` | `"App"`| Specifies the class name of the handler, which will be used as the lambda function name. It should use camel case.
`version` | 1.0-SNAPSHOT | Specifies the version of the project
`package` | ${groupId} | Specifies the package name for the classes


[aws-java-sdk-v2]: https://github.com/aws/aws-sdk-java-v2
[deploying-lambda-apps]: https://docs.aws.amazon.com/lambda/latest/dg/deploying-lambda-apps.html
[sam-cli]:https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-getting-started.html

## References
Maven archetype: https://maven.apache.org/archetype/maven-archetype-plugin/usage.html