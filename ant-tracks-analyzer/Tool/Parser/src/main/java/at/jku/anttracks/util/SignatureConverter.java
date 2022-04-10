
package at.jku.anttracks.util;

import at.jku.anttracks.heap.symbols.AllocatedType;

public class SignatureConverter {

    private final static char FULLY_QUALIFIED_CLASS = 'L';
    private final static char ARRAY = '[';
    private final static char VOID = 'V';

    private final static char BOOLEAN = 'Z';
    private final static char BYTE = 'B';
    private final static char CHAR = 'C';
    private final static char SHORT = 'S';
    private final static char INT = 'I';
    private final static char LONG = 'J';
    private final static char FLOAT = 'F';
    private final static char DOUBLE = 'D';
    private final static char UNKNOWN = AllocatedType.ALLOCATED_TYPE_IDENTIFIER_UNKNOWN_SIG;

    public static String convertToJavaMethodSignature(String original) {
        return convertToJavaMethodSignature(original, false, false, false);
    }

    public static String convertToJavaMethodSignature(String original, boolean omitPackage) {
        return convertToJavaMethodSignature(original, omitPackage, false, false);
    }

    public static String convertToJavaMethodSignature(String original, boolean omitPackage, boolean omitArgs, boolean omitReturnType) {
        int paramStart = original.indexOf('(');
        int paramEnd = original.indexOf(')');

        if (paramStart >= 0 && paramEnd >= 0 && original.contains(".")) {
            String signature = original.substring(0, paramStart);
            String declaringType = signature.substring(0, signature.lastIndexOf('.'));
            String method = signature.substring(declaringType.length() + 1);
            String args = convertToJavaType(original.substring(paramStart + 1, paramEnd), omitPackage);
            String returnType = convertToJavaType(original.substring(paramEnd + 1, original.length()), omitPackage);

            // switch(method) {
            // case "<init>": method = declaringType.contains(".") ? declaringType.substring(declaringType.lastIndexOf('.') + 1) :
            // declaringType; break;
            // case "<clinit>": method = "static"; break;
            // }

            if (omitPackage) {
                declaringType = declaringType.substring(declaringType.lastIndexOf('.') + 1);
            }

            return declaringType + '.' + method +
                    (omitArgs ? "" : '(' + args + ')') +
                    (omitReturnType ? "" : " : " + returnType);
        } else {
            return original; // VM_INTERNAL;
        }

    }

    public static String convertToJavaType(String typeSignature) {
        return convertToJavaType(typeSignature, false);
    }

    public static String convertToJavaType(String typeSignatures, boolean omitPackage) {
        StringBuilder result = new StringBuilder();

        while (typeSignatures.length() > 0) {
            if (result.length() > 0) {
                result.append(", ");
            }

            int arrayRank = 0;
            for (int i = 0; typeSignatures.charAt(i) == ARRAY; i++) {
                arrayRank++;
            }
            typeSignatures = typeSignatures.substring(arrayRank);

            String type;
            if (typeSignatures.charAt(0) == FULLY_QUALIFIED_CLASS) {
                type = typeSignatures.substring(1, typeSignatures.indexOf(';'));
                typeSignatures = typeSignatures.substring(type.length() + 2);
                type = type.replace('/', '.');
                if (omitPackage && type.contains(".")) {
                    type = type.substring(type.lastIndexOf('.') + 1);
                }
            } else {
                switch (typeSignatures.charAt(0)) {
                    case VOID:
                        type = "void";
                        break;
                    case BOOLEAN:
                        type = "boolean";
                        break;
                    case CHAR:
                        type = "char";
                        break;
                    case BYTE:
                        type = "byte";
                        break;
                    case SHORT:
                        type = "short";
                        break;
                    case INT:
                        type = "int";
                        break;
                    case LONG:
                        type = "long";
                        break;
                    case FLOAT:
                        type = "float";
                        break;
                    case DOUBLE:
                        type = "double";
                        break;
                    case UNKNOWN:
                        type = "unknown";
                        break;
                    default:
                        assert false;
                        type = null;
                        break;
                }
                typeSignatures = typeSignatures.substring(1);
            }

            result.append(type);
            for (int i = 0; i < arrayRank; i++) {
                result.append("[]");
            }
        }

        return result.toString();
    }

    public static String convertExternalNameToInternal(String externalClassName) {
        if (externalClassName.startsWith("" + ARRAY)) { return externalClassName; }
        StringBuilder internal = new StringBuilder(externalClassName);
        int arrayCount = 0;
        while (internal.toString().endsWith("[]")) {
            internal = new StringBuilder(internal.substring(0, internal.length() - 2));
            arrayCount++;
        }
        switch (internal.toString()) {
            case "boolean":
                internal = new StringBuilder("" + BOOLEAN);
                break;
            case "char":
                internal = new StringBuilder("" + CHAR);
                break;
            case "float":
                internal = new StringBuilder("" + FLOAT);
                break;
            case "double":
                internal = new StringBuilder("" + DOUBLE);
                break;
            case "byte":
                internal = new StringBuilder("" + BYTE);
                break;
            case "short":
                internal = new StringBuilder("" + SHORT);
                break;
            case "int":
                internal = new StringBuilder("" + INT);
                break;
            case "long":
                internal = new StringBuilder("" + LONG);
                break;
            default: {
                internal = new StringBuilder(FULLY_QUALIFIED_CLASS + internal.toString() + ";");
            }
        }
        while (arrayCount > 0) {
            internal.insert(0, ARRAY);
            arrayCount--;
        }
        return internal.toString();
    }
}
