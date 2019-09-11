package code.annotation;

import java.lang.annotation.*;

/**
 * @author 47 1
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Controller {
    String value() default "";
}
