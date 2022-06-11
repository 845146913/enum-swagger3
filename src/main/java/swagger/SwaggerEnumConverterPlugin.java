package swagger;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import io.swagger.annotations.ApiModelProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.util.ReflectionUtils;
import springfox.documentation.builders.PropertySpecificationBuilder;
import springfox.documentation.schema.ModelSpecification;
import springfox.documentation.schema.property.ModelSpecificationFactory;
import springfox.documentation.service.AllowableListValues;
import springfox.documentation.service.AllowableRangeValues;
import springfox.documentation.service.AllowableValues;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.schema.ModelPropertyBuilderPlugin;
import springfox.documentation.spi.schema.contexts.ModelPropertyContext;
import springfox.documentation.swagger.common.SwaggerPluginSupport;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.springframework.util.StringUtils.hasText;
import static org.springframework.util.StringUtils.isEmpty;
import static springfox.documentation.schema.Annotations.findPropertyAnnotation;
import static springfox.documentation.swagger.schema.ApiModelProperties.findApiModePropertyAnnotation;


/**
 * ApiModelProperty解析枚举键值插件
 *
 * @Author: wangsp
 * @Date: 2021/5/12
 */
@Order(SwaggerPluginSupport.OAS_PLUGIN_ORDER)
@RequiredArgsConstructor
@Slf4j
public class SwaggerEnumConverterPlugin extends SwaggerEnumParser implements ModelPropertyBuilderPlugin {
    private final ModelSpecificationFactory modelSpecifications;

    @Override
    public void apply(ModelPropertyContext context) {
        Optional<ApiModelProperty> ann = Optional.empty();
        if (context.getAnnotatedElement().isPresent()) {
            ann =
                    ann.map(Optional::of)
                            .orElse(findApiModePropertyAnnotation(context.getAnnotatedElement().get()));
        }
        if (context.getBeanPropertyDefinition().isPresent()) {
            ann = ann.map(Optional::of).orElse(findPropertyAnnotation(
                    context.getBeanPropertyDefinition().get(),
                    ApiModelProperty.class));
        }
        if (ann.isPresent()) {
            Class<?> rawType = context.getBeanPropertyDefinition().get().getField().getRawType();
            if (Number.class.isAssignableFrom(rawType)) {
                context.getSpecificationBuilder().example("1");
            }
            if (Boolean.class.isAssignableFrom(rawType)) {
                context.getSpecificationBuilder().example("true");
            }
            //if (Enum.class.isAssignableFrom(rawType)) {
            if (modelSpecifications.getEnumTypeDeterminer().isEnum(rawType)) {


                String displayValue = parseSwaggerEnumAnnotation(rawType);
                // description
                Field description = ReflectionUtils.findField(PropertySpecificationBuilder.class, "description");
                description.setAccessible(true);
                String descriptionValue = (String) ReflectionUtils.getField(description, context.getSpecificationBuilder());
                displayValue = Objects.nonNull(descriptionValue) ? descriptionValue + displayValue : displayValue;
                ModelSpecification modelSpecification =
                        ann.map(a -> {
                            if (!a.dataType().isEmpty()) {
                                return modelSpecifications
                                        .create(context.getOwner(), toType(context.getResolver()).apply(a));
                            }
                            ResolvedType resolvedType = getBodyEnumResolvedType(context.getResolver(), rawType);
                            if (Objects.nonNull(resolvedType)) {
                                return modelSpecifications
                                        .create(context.getOwner(), resolvedType);
                            }
                            return null;
                        })
                                .orElse(null);
                Optional<ApiModelProperty> finalAnnotation = ann;
                context.getSpecificationBuilder().description(displayValue)
                        .type(modelSpecification)
                        .readOnly(ann.map(ApiModelProperty::readOnly).orElse(false))
                        .isHidden(ann.map(ApiModelProperty::hidden).orElse(false))
                        .position(ann.map(ApiModelProperty::position).orElse(0))
                        .required(ann.map(ApiModelProperty::required).orElse(false))
                        .example(ann.map(toExample()).orElse(""))
                        .enumerationFacet(e -> e.allowedValues(finalAnnotation.map(toAllowableValues()).orElse(null)));

                context.getBuilder().description(displayValue)
                        .type(ann.map(a -> {
                            ResolvedType resolvedType = getBodyEnumResolvedType(context.getResolver(), rawType);
                            if (Objects.nonNull(resolvedType)) {
                                return resolvedType;
                            }
                            return toType(context.getResolver()).apply(a);
                        }).orElse(null))
                        .allowableValues(ann.map(toAllowableValues()).orElse(null))
                        .required(ann.map(ApiModelProperty::required).orElse(false))
                        .readOnly(ann.map(ApiModelProperty::readOnly).orElse(false))
                        .isHidden(ann.map(ApiModelProperty::hidden).orElse(false))
                        .position(ann.map(ApiModelProperty::position).orElse(0))
                        .example(ann.map(toExample()).orElse(""));
            }
        }


    }


    static Function<ApiModelProperty, ResolvedType> toType(final TypeResolver resolver) {
        return annotation -> {
            try {
                return resolver.resolve(Class.forName(annotation.dataType()));
            } catch (ClassNotFoundException e) {
                return resolver.resolve(Object.class);
            }
        };
    }

    static Function<ApiModelProperty, String> toExample() {
        return annotation -> {
            String example = "";
            if (!isEmpty(annotation.example())) {
                example = annotation.example();
            }
            return example;
        };
    }

    static Function<ApiModelProperty, AllowableValues> toAllowableValues() {
        return annotation -> allowableValueFromString(annotation.allowableValues());
    }

    @SuppressWarnings("java:S4784")
    private static final Pattern RANGE_PATTERN = Pattern.compile("range([\\[(])(.*),(.*)([])])$");

    public static AllowableValues allowableValueFromString(String allowableValueString) {
        AllowableValues allowableValues = new AllowableListValues(new ArrayList<String>(), "LIST");
        String trimmed = allowableValueString.trim();
        Matcher matcher = RANGE_PATTERN.matcher(trimmed.replaceAll(" ", ""));
        if (matcher.matches()) {
            if (matcher.groupCount() != 4) {
                log.warn("Unable to parse range specified {} correctly", trimmed);
            } else {
                allowableValues = new AllowableRangeValues(
                        matcher.group(2).contains("infinity") ? null : matcher.group(2),
                        matcher.group(1).equals("("),
                        matcher.group(3).contains("infinity") ? null : matcher.group(3),
                        matcher.group(4).equals(")"));
            }
        } else if (trimmed.contains(",")) {
            List<String> split =
                    Stream.of(trimmed.split(",")).map(String::trim).filter(item -> !item.isEmpty()).collect(toList());
            allowableValues = new AllowableListValues(split, "LIST");
        } else if (hasText(trimmed)) {
            List<String> singleVal = singletonList(trimmed);
            allowableValues = new AllowableListValues(singleVal, "LIST");
        }
        return allowableValues;
    }


    @Override
    public boolean supports(DocumentationType documentationType) {
        return SwaggerPluginSupport.pluginDoesApply(documentationType);
    }
}