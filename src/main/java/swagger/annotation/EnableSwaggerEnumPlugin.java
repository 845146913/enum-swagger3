package swagger.annotation;


import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import swagger.SwaggerEnumConverterPlugin;
import swagger.SwaggerEnumParameterBuilderPlugin;

import java.lang.annotation.*;

/**
 * 开启swagger枚举展示键值配置
 *
 * @Author: wangsp
 * @Date: 2021/5/8
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({SwaggerEnumConverterPlugin.class, SwaggerEnumParameterBuilderPlugin.class})
@Component
public @interface EnableSwaggerEnumPlugin {
}
