package zapsolutions.zap.protoc;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.compiler.PluginProtos;

import java.io.IOException;

import static zapsolutions.zap.protoc.Constants.ClassNames.FQ_NAME_GRPC_CHANNEL;
import static zapsolutions.zap.protoc.Constants.ClassNames.FQ_NAME_GRPC_CREDENTIALS;
import static zapsolutions.zap.protoc.Constants.ClassNames.FQ_NAME_LNDMOBILE;
import static zapsolutions.zap.protoc.Constants.ClassNames.FQ_NAME_RPC_PACKAGE_PREFIX;
import static zapsolutions.zap.protoc.Constants.ClassNames.FQ_NAME_OBSERVABLE;
import static zapsolutions.zap.protoc.Constants.ClassNames.FQ_NAME_PROTOCOL_EX;
import static zapsolutions.zap.protoc.Constants.ClassNames.FQ_NAME_SINGLE;
import static zapsolutions.zap.protoc.Constants.ClassNames.FQ_NAME_ZAP_LND;
import static zapsolutions.zap.protoc.Constants.Defaults.BRACES_CLOSE;
import static zapsolutions.zap.protoc.Constants.Defaults.BRACES_OPEN;
import static zapsolutions.zap.protoc.Constants.Defaults.BRACKET_CLOSE;
import static zapsolutions.zap.protoc.Constants.Defaults.BRACKET_OPEN;
import static zapsolutions.zap.protoc.Constants.Defaults.EMPTY;
import static zapsolutions.zap.protoc.Constants.Defaults.NEWLINE;
import static zapsolutions.zap.protoc.Constants.Defaults.SEMICOLON;
import static zapsolutions.zap.protoc.Constants.Defaults.SPACE;
import static zapsolutions.zap.protoc.Constants.Defaults.TAB;
import static zapsolutions.zap.protoc.Constants.Java.CLASS;
import static zapsolutions.zap.protoc.Constants.Java.IMPLEMENTS;
import static zapsolutions.zap.protoc.Constants.Java.IMPORT;
import static zapsolutions.zap.protoc.Constants.Java.INTERFACE;
import static zapsolutions.zap.protoc.Constants.Java.OVERRIDE;
import static zapsolutions.zap.protoc.Constants.Java.PACKAGE;
import static zapsolutions.zap.protoc.Constants.Java.PRIVATE;
import static zapsolutions.zap.protoc.Constants.Java.PUBLIC;
import static zapsolutions.zap.protoc.Helper.getStreamingType;

public class ProtocPlugin {

    private static final String LOCAL = "Local";
    private static final String REMOTE = "Remote";

    public static void main(String[] args) throws IOException {

        PluginProtos.CodeGeneratorRequest codeGeneratorRequest = PluginProtos.CodeGeneratorRequest.parseFrom(System.in);
        PluginProtos.CodeGeneratorResponse.Builder responseBuilder = PluginProtos.CodeGeneratorResponse.newBuilder();

        codeGeneratorRequest.getProtoFileList().forEach((DescriptorProtos.FileDescriptorProto fileDescriptorProto) -> {

            fileDescriptorProto.getServiceList().forEach((DescriptorProtos.ServiceDescriptorProto serviceDescriptor) -> {
                // interface
                PluginProtos.CodeGeneratorResponse.File.Builder interfaceBuilder = responseBuilder.addFileBuilder();
                generateInterface(interfaceBuilder, serviceDescriptor);
                interfaceBuilder.build();

                // implementations
                PluginProtos.CodeGeneratorResponse.File.Builder localImplementationBuilder = responseBuilder.addFileBuilder();
                generateImplementation(localImplementationBuilder, serviceDescriptor, LOCAL, fileDescriptorProto);
                localImplementationBuilder.build();

                PluginProtos.CodeGeneratorResponse.File.Builder remoteImplementationBuilder = responseBuilder.addFileBuilder();
                generateImplementation(remoteImplementationBuilder, serviceDescriptor, REMOTE, fileDescriptorProto);
                remoteImplementationBuilder.build();
            });

        });

        PluginProtos.CodeGeneratorResponse response = responseBuilder.build();
        response.writeTo(System.out);
    }

    private static void generateInterface(PluginProtos.CodeGeneratorResponse.File.Builder fileBuilder, DescriptorProtos.ServiceDescriptorProto serviceDescriptorProto) {
        StringBuilder fileContent = new StringBuilder();
        String serviceName = "Lnd" + serviceDescriptorProto.getName() + "Service";
        fileBuilder.setName(serviceName + ".java");

        // heading
        fileContent.append(PACKAGE).append(SPACE).append(FQ_NAME_ZAP_LND).append(SEMICOLON);
        fileContent.append(NEWLINE);

        appendInterfaceImports(fileContent);

        // start interface
        startInterface(fileContent, serviceName);

        // methods
        serviceDescriptorProto.getMethodList().forEach((DescriptorProtos.MethodDescriptorProto methodDescriptor)
                -> {
            Constants.StreamingType streamingType = getStreamingType(methodDescriptor);
            switch (streamingType) {
                case TYPE_UNARY:
                case TYPE_SERVER:
                    appendSignature(fileContent, methodDescriptor, EMPTY, streamingType);
                    fileContent.append(NEWLINE);
                    break;
                case TYPE_CLIENT:
                case TYPE_BIDIRECT:
                default:
                    appendSkipped(fileContent, methodDescriptor);
                    fileContent.append(NEWLINE);
            }
        });

        endClass(fileContent);

        fileBuilder.setContent(fileContent.toString());
    }

    private static void startInterface(StringBuilder stringBuilder, String name) {
        stringBuilder.append(PUBLIC)
                .append(SPACE)
                .append(INTERFACE)
                .append(SPACE)
                .append(name)
                .append(SPACE)
                .append(BRACES_OPEN)
                .append(NEWLINE);
    }

    private static void endClass(StringBuilder stringBuilder) {
        stringBuilder.append(BRACES_CLOSE);
    }

    private static void startClass(StringBuilder stringBuilder, String className, String interfaceName) {
        stringBuilder.append(PUBLIC)
                .append(SPACE)
                .append(CLASS)
                .append(SPACE)
                .append(className)
                .append(SPACE)
                .append(IMPLEMENTS)
                .append(SPACE)
                .append(interfaceName)
                .append(SPACE)
                .append(BRACES_OPEN)
                .append(NEWLINE)
                .append(NEWLINE);
    }

    private static void generateImplementation(PluginProtos.CodeGeneratorResponse.File.Builder fileBuilder, DescriptorProtos.ServiceDescriptorProto serviceDescriptorProto, String type, DescriptorProtos.FileDescriptorProto fileDescriptorProto) {
        StringBuilder fileContent = new StringBuilder();
        String serviceName = serviceDescriptorProto.getName();
        String packageName = fileDescriptorProto.getPackage();
        String serviceNameInterface = "Lnd" + serviceName + "Service";
        String serviceNameImplementation = type + "Lnd" + serviceName + "Service";
        fileBuilder.setName(serviceNameImplementation + ".java");

        // heading
        fileContent.append(PACKAGE).append(SPACE).append(FQ_NAME_ZAP_LND).append(SEMICOLON);
        fileContent.append(NEWLINE);

        appendImplementationImports(fileContent, type, serviceName, packageName);

        // start class
        startClass(fileContent, serviceNameImplementation, serviceNameInterface);

        // constructor for remote
        if (type.equals(REMOTE)) {
            appendRemoteConstructorBlob(fileContent, serviceName);
        }

        // methods
        serviceDescriptorProto.getMethodList().forEach((DescriptorProtos.MethodDescriptorProto methodDescriptor)
                -> {
            Constants.StreamingType streamingType = getStreamingType(methodDescriptor);
            switch (streamingType) {
                case TYPE_UNARY:
                case TYPE_SERVER:
                    appendMethod(fileContent, methodDescriptor, type, streamingType);
                    break;
                case TYPE_CLIENT:
                case TYPE_BIDIRECT:
                default:
                    // skip
            }
        });

        endClass(fileContent);

        // write
        fileBuilder.setContent(fileContent.toString());
    }

    private static void appendMethod(StringBuilder stringBuilder, DescriptorProtos.MethodDescriptorProto methodDescriptorProto, String type, Constants.StreamingType streamingType) {
        stringBuilder.append(TAB).append(OVERRIDE);
        appendSignature(stringBuilder, methodDescriptorProto, PUBLIC, streamingType);
        stringBuilder.deleteCharAt(stringBuilder.length() - 1); // remove semicolon
        stringBuilder.append(SPACE).append(BRACES_OPEN).append(NEWLINE);

        String methodName = Character.toLowerCase(methodDescriptorProto.getName().charAt(0)) + methodDescriptorProto.getName().substring(1);
        if (type.equals(LOCAL)) {
            String returnType = FQ_NAME_RPC_PACKAGE_PREFIX + methodDescriptorProto.getOutputType();
            appendLocalMethodBlob(stringBuilder, methodName, returnType, streamingType);
        } else if (type.equals(REMOTE)) {
            appendRemoteMethodBlob(stringBuilder, methodName, streamingType);
        }

        stringBuilder.append(TAB).append(BRACES_CLOSE).append(NEWLINE).append(NEWLINE);
    }

    private static void appendRemoteConstructorBlob(StringBuilder stringBuilder, String serviceName) {
        stringBuilder.append(PRIVATE).append(SPACE).append(serviceName).append("Grpc.").append(serviceName).append("Stub asyncStub;" + NEWLINE);
        stringBuilder.append(PUBLIC).append(SPACE).append("RemoteLnd").append(serviceName).append("Service(Channel channel, CallCredentials callCredentials) {" + NEWLINE);
        stringBuilder.append(TAB + "asyncStub = ").append(serviceName).append("Grpc.newStub(channel).withCallCredentials(callCredentials);" + NEWLINE);
        stringBuilder.append(BRACES_CLOSE).append(NEWLINE).append(NEWLINE);
    }

    private static void appendLocalMethodBlob(StringBuilder stringBuilder, String methodName, String returnType, Constants.StreamingType streamingType) {
        String s = String.join(NEWLINE
                , TAB + "return %s.createDefault(emitter -> Lndmobile.%s(request.toByteArray(), new %s<%s>(emitter) {"
                , OVERRIDE
                , "%s parseResponse(byte[] bytes) throws InvalidProtocolBufferException {"
                , "if(bytes == null) {"
                , "return %s.newBuilder().build();"
                , "} else {"
                , "return %s.parseFrom(bytes);"
                , "}"
                , BRACES_CLOSE
                , BRACES_CLOSE + "));");

        String wrappedType;
        String observer;
        switch (streamingType) {
            case TYPE_UNARY:
                wrappedType = "DefaultSingle";
                observer = "LocalLndSingleObserver";
                break;
            case TYPE_SERVER:
                wrappedType = "DefaultObservable";
                observer = "LocalLndStreamObserver";
                break;
            default:
                throw new IllegalStateException("Invalid type: " + streamingType);
        }

        String format = String.format(s, wrappedType, methodName, observer, returnType, returnType, returnType, returnType);
        stringBuilder.append(format);
    }

    private static void appendRemoteMethodBlob(StringBuilder stringBuilder, String methodName, Constants.StreamingType streamingType) {
        switch (streamingType) {
            case TYPE_UNARY:
                stringBuilder.append(TAB + TAB + "return DefaultSingle.createDefault(emitter -> asyncStub.").append(methodName).append("(request, new RemoteLndSingleObserver<>(emitter)));").append(NEWLINE);
                break;
            case TYPE_SERVER:
                stringBuilder.append(TAB + TAB + "return DefaultObservable.createDefault(emitter -> asyncStub.").append(methodName).append("(request, new RemoteLndStreamObserver<>(emitter)));").append(NEWLINE);
                break;
            default:
                throw new IllegalStateException("Invalid type: " + streamingType);
        }
    }

    private static void appendInterfaceImports(StringBuilder fileContent) {
        fileContent.append(NEWLINE);
        fileContent.append(IMPORT).append(SPACE).append(FQ_NAME_OBSERVABLE).append(SEMICOLON).append(NEWLINE);
        fileContent.append(IMPORT).append(SPACE).append(FQ_NAME_SINGLE).append(SEMICOLON).append(NEWLINE);
        fileContent.append(NEWLINE);
    }

    private static void appendImplementationImports(StringBuilder fileContent, String type, String serviceName, String packageName) {
        fileContent.append(NEWLINE);
        fileContent.append(IMPORT).append(SPACE).append(FQ_NAME_OBSERVABLE).append(SEMICOLON).append(NEWLINE);
        fileContent.append(IMPORT).append(SPACE).append(FQ_NAME_SINGLE).append(SEMICOLON).append(NEWLINE);

        switch (type) {
            case LOCAL:
                fileContent.append(IMPORT).append(SPACE).append(FQ_NAME_LNDMOBILE).append(SEMICOLON).append(NEWLINE);
                fileContent.append(IMPORT).append(SPACE).append(FQ_NAME_PROTOCOL_EX).append(SEMICOLON).append(NEWLINE);
                break;
            case REMOTE:
                fileContent.append(IMPORT).append(SPACE).append(FQ_NAME_RPC_PACKAGE_PREFIX).append(".").append(packageName).append(".").append(serviceName).append("Grpc").append(SEMICOLON).append(NEWLINE);
                fileContent.append(IMPORT).append(SPACE).append(FQ_NAME_GRPC_CREDENTIALS).append(SEMICOLON).append(NEWLINE);
                fileContent.append(IMPORT).append(SPACE).append(FQ_NAME_GRPC_CHANNEL).append(SEMICOLON).append(NEWLINE);
                break;
            default:
                throw new IllegalStateException("Invalid type: " + type);
        }

        fileContent.append(NEWLINE);
    }

    private static void appendSkipped(StringBuilder data, DescriptorProtos.MethodDescriptorProto methodDescriptorProto) {
        data.append(NEWLINE);
        data.append(TAB);
        data.append("// skipped " + methodDescriptorProto.getName());
    }

    private static void appendSignature(StringBuilder data, DescriptorProtos.MethodDescriptorProto methodDescriptorProto, String modifier, Constants.StreamingType streamingType) {
        data.append(NEWLINE);
        String fqNameRequest = FQ_NAME_RPC_PACKAGE_PREFIX + methodDescriptorProto.getInputType();
        String returnType = FQ_NAME_RPC_PACKAGE_PREFIX + methodDescriptorProto.getOutputType();
        String methodName = Character.toLowerCase(methodDescriptorProto.getName().charAt(0)) + methodDescriptorProto.getName().substring(1);

        data.append(TAB);

        if (!modifier.isEmpty()) {
            data.append(modifier).append(SPACE);
        }

        if (streamingType.equals(Constants.StreamingType.TYPE_UNARY)) {
            data.append(String.format(Constants.ReturnType.TYPED_SINGLE, returnType));
        } else {
            data.append(String.format(Constants.ReturnType.TYPED_OBSERVABLE, returnType));
        }

        data.append(methodName)
                .append(BRACKET_OPEN)
                .append(fqNameRequest)
                .append(" request")
                .append(BRACKET_CLOSE)
                .append(SEMICOLON);
    }
}