package swagger.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *  该注解用于处理swagger展示枚举类键值对【key(code)-value(message)】转换信息
 *  用法：1.SwaggerEnum注解在对应的枚举类即可
 *        2.不添加此注解则解析默认的枚举key(下标)-value(枚举常量)值
 *
 * @ApiModelProperty 返回实体字段需要相应swagger api注解
 * @Author: wangsp
 * @Date: 2021/5/12
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SwaggerEnum {
    /**
     * 默认的code字段名
     * @return
     */
    String codeName() default "code";

    /**
     * 默认message字段名
     * @return
     */
    String messageName() default "message";
}
