package swagger;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import swagger.annotation.SwaggerEnum;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author wangsp
 * @since 2022/5/24
 */
public class SwaggerEnumParser {


    /**
     * @param rawType 枚举类
     * @return description
     */
    public String parseSwaggerEnumAnnotation(Class<?> rawType) {
        SwaggerEnum enumAnn = rawType.getAnnotation(SwaggerEnum.class);
        Field codeField = null;
        Field messageField = null;
        if (enumAnn != null) {
            String codeName = enumAnn.codeName();
            String messageName = enumAnn.messageName();
            if (StringUtils.isEmpty(codeName) || StringUtils.isEmpty(messageName)) {
                return "";
            }
            codeField = ReflectionUtils.findField(rawType, codeName);
            messageField = ReflectionUtils.findField(rawType, messageName);

        }
        String displayValue = resolverEnumConstants(rawType, codeField, messageField);
        return displayValue;
    }

    /**
     * 解析枚举的key-value值
     *
     * @param rawType
     * @param codeField
     * @param messageField
     * @return
     */
    public String resolverEnumConstants(Class<?> rawType, Field codeField, Field messageField) {
        String displayValue;
        Enum[] enumConstants = (Enum[]) rawType.getEnumConstants();
        if (Objects.isNull(codeField) && Objects.isNull(messageField)) {
            // default enum
            displayValue = Stream.of(enumConstants)
                    .map(e -> e.ordinal() + ":" + e.toString())
                    .collect(Collectors.joining(";", ",(", ")"));
        } else if (Objects.nonNull(codeField) && Objects.isNull(messageField)) {
            displayValue = Stream.of(enumConstants)
                    .map(e -> {
                        ReflectionUtils.makeAccessible(codeField);
                        Object code = ReflectionUtils.getField(codeField, e);
                        return code + ":" + e.toString();
                    })
                    .collect(Collectors.joining(";", "(", ")"));
        } else {
            displayValue = Stream.of(enumConstants)
                    .map(e -> {
                        ReflectionUtils.makeAccessible(codeField);
                        ReflectionUtils.makeAccessible(messageField);
                        Object code = ReflectionUtils.getField(codeField, e);
                        Object message = ReflectionUtils.getField(messageField, e);
                        return code + ":" + message;
                    })
                    .collect(Collectors.joining(";", "(", ")"));
        }
        return displayValue;
    }

    public ResolvedType getBodyEnumResolvedType(TypeResolver resolver, Class<?> erasedType) {
        ResolvedType resolved = null;
        Type[] genericInterfaces = erasedType.getGenericInterfaces();
        if (genericInterfaces.length < 1) {
            return null;
        }
        ParameterizedType parameterizedType = (ParameterizedType) genericInterfaces[0];
        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
        if (actualTypeArguments.length > 1) {
            Type actualTypeArgument = actualTypeArguments[1];
            resolved = Optional.of(resolver.resolve(actualTypeArgument)).orElse(null);
        }
        return resolved;
    }
}