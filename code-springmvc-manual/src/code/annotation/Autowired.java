package code.annotation;

import java.lang.annotation.*;

/**
 * @author 47 1
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Autowired {
    String value() default "";
}
