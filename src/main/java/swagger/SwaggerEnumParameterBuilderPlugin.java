package swagger;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import springfox.documentation.builders.ModelSpecificationBuilder;
import springfox.documentation.schema.ModelSpecification;
import springfox.documentation.schema.ScalarType;
import springfox.documentation.schema.ScalarTypes;
import springfox.documentation.service.AllowableListValues;
import springfox.documentation.service.AllowableValues;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.schema.EnumTypeDeterminer;
import springfox.documentation.spi.service.ExpandedParameterBuilderPlugin;
import springfox.documentation.spi.service.contexts.ParameterExpansionContext;
import springfox.documentation.spring.web.DescriptionResolver;
import springfox.documentation.swagger.common.SwaggerPluginSupport;
import springfox.documentation.swagger.schema.ApiModelProperties;
import swagger.annotation.SwaggerEnum;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static springfox.documentation.swagger.common.SwaggerPluginSupport.OAS_PLUGIN_ORDER;

/**
 * @author wangsp
 * @since 2022/5/24
 */
@RequiredArgsConstructor
@Slf4j
@Order(SwaggerEnumParameterBuilderPlugin.ENUM_PLUGIN_ORDER)
public class SwaggerEnumParameterBuilderPlugin extends SwaggerEnumParser implements ExpandedParameterBuilderPlugin {
    private final TypeResolver resolver;
    private final DescriptionResolver descriptions;
    private final EnumTypeDeterminer enumTypeDeterminer;
    public static final int ENUM_PLUGIN_ORDER = OAS_PLUGIN_ORDER + 2;

    @Override
    public void apply(ParameterExpansionContext context) {
        Optional<ApiModelProperty> apiModelPropertyOptional = context.findAnnotation(ApiModelProperty.class);
        apiModelPropertyOptional.ifPresent(apiModelProperty -> fromApiModelProperty(context, apiModelProperty));
        Optional<ApiParam> apiParamOptional = context.findAnnotation(ApiParam.class);
        apiParamOptional.ifPresent(apiParam -> fromApiParam(context, apiParam));
    }

    private void fromApiModelProperty(
            ParameterExpansionContext context,
            ApiModelProperty apiModelProperty) {
        String allowableProperty = ofNullable(apiModelProperty.allowableValues())
                .filter(((Predicate<String>) String::isEmpty).negate()).orElse(null);
        Class<?> erasedType = context.getFieldType().getErasedType();
        AllowableValues allowable = allowableValues(
                ofNullable(allowableProperty),
                erasedType);

        String resolve = descriptions.resolve(apiModelProperty.value());
        resolve = resolveEnumDescription(erasedType, resolve);
        ResolvedType resolved = enumTypeDeterminer.isEnum(erasedType) ? getBodyEnumResolvedType(resolver, erasedType) : null;
        ModelSpecification modelSpecification = null;
        if (Objects.nonNull(resolved)) {
            modelSpecification = new ModelSpecificationBuilder()
                    .scalarModel(ScalarTypes.builtInScalarType(resolved)
                            .orElse(ScalarType.STRING))
                    .build();
        }
        ModelSpecification modelSpecificationFinal = modelSpecification;
        context.getParameterBuilder()
                .description(resolve)
                .allowableValues(allowable)
                .order(ENUM_PLUGIN_ORDER)
                .build();

        context.getRequestParameterBuilder()
                .description(resolve)
                .precedence(ENUM_PLUGIN_ORDER)
                .query(q -> q.enumerationFacet(e -> e.allowedValues(allowable))
                        .model(mb -> mb.copyOf(modelSpecificationFinal)));
    }


    private String resolveEnumDescription(Class<?> erasedType, String resolve) {
        if (Enum.class.isAssignableFrom(erasedType)) {
            resolve = resolve + parseSwaggerEnumAnnotation(erasedType);
        }
        return resolve;
    }

    private void fromApiParam(
            ParameterExpansionContext context,
            ApiParam apiParam) {
        String allowableProperty =
                ofNullable(apiParam.allowableValues())
                        .filter(((Predicate<String>) String::isEmpty).negate())
                        .orElse(null);
        Class<?> erasedType = context.getFieldType().getErasedType();
        String resolve = descriptions.resolve(apiParam.value());
        resolve = resolveEnumDescription(erasedType, resolve);

        AllowableValues allowable = allowableValues(
                ofNullable(allowableProperty),
                erasedType);
        context.getParameterBuilder()
                .description(resolve)
                .allowableValues(allowable)
                .order(ENUM_PLUGIN_ORDER)
                .build();

        context.getRequestParameterBuilder()
                .description(resolve)
                .precedence(ENUM_PLUGIN_ORDER)
                .query(q -> q.enumerationFacet(e -> e.allowedValues(allowable)));
    }

    private AllowableValues allowableValues(
            final Optional<String> optionalAllowable,
            Class<?> fieldType) {

        AllowableValues allowable = null;
        if (enumTypeDeterminer.isEnum(fieldType)) {
            allowable = new AllowableListValues(getEnumValues(fieldType), "LIST");
        } else if (optionalAllowable.isPresent()) {
            allowable = ApiModelProperties.allowableValueFromString(optionalAllowable.get());
        }
        return allowable;
    }

    private List<String> getEnumValues(final Class<?> subject) {
        SwaggerEnum annotation = subject.getAnnotation(SwaggerEnum.class);
        if (Objects.nonNull(annotation)) {
            String codeName = annotation.codeName();
            List<String> strings = ofNullable(ReflectionUtils.findField(subject, codeName))
                    .map(field ->
                            Stream.of(subject.getEnumConstants()).map((Function<Object, String>) mapper -> {
                                ReflectionUtils.makeAccessible(field);
                                return ReflectionUtils.getField(field, mapper).toString();
                            }).collect(toList())
                    ).orElse(new ArrayList<>());
            if (!CollectionUtils.isEmpty(strings)) {
                return strings;
            }
        }
        return Stream.of(subject.getEnumConstants())
                .map((Function<Object, String>) Object::toString)
                .collect(toList());
    }

    @Override
    public boolean supports(DocumentationType documentationType) {
        return SwaggerPluginSupport.pluginDoesApply(documentationType);
    }
}