package code.servlet.version2;

import code.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

/**
 * @author 47 1
 */
public class DispatcherServlet extends HttpServlet {

    /**
     * 保存application.properties配置文件里的内容
     */
    private Properties contextConfig = new Properties();

    /**
     * 保存所扫描到的类
     */
    private List<String> classNames = new ArrayList<>();

    /**
     * 传说中的IOC容器
     * 为了简化程序，暂时用HashMap代替ConcurrentHashMap
     */
    private Map<String, Object> ioc = new HashMap<>();

    /**
     * 保存url和Method的关系
     */
    private Map<String, Method> handlerMapping = new HashMap<>();


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            //委派模式
            doDispatcher(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exception " + Arrays.toString(e.getStackTrace()));
        }
    }


    private void doDispatcher(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String url = req.getRequestURI();
        if(!this.handlerMapping.containsKey(url)){
            resp.getWriter().write("404 Not Found");
            return;
        }
        Method method = this.handlerMapping.get(url);
        //第一个参数，方法所在的实例
        //第二个参数，调用时所需要的实参
        Map<String,String[]> params = req.getParameterMap();
        //获取方法的参数列表
        Class<?>[] parameterTypes = method.getParameterTypes();
        //保存赋值参数的位置
        Object [] paramValues = new Object[parameterTypes.length];
        //根据参数位置动态赋值
        for(int i = 0;i<parameterTypes.length;i++){
            Class parameterType = parameterTypes[i];
            if(parameterType == HttpServletRequest.class){
                paramValues[i]=req;
                continue;
            }else if(parameterType == HttpServletResponse.class){
                paramValues[i]=resp;
                continue;
            }else if(parameterType == String.class){
                //提取方法中加了注解的参数
                Annotation[][] pa = method.getParameterAnnotations();
                for(int j = 0;j<pa.length;j++){
                    for (Annotation a : pa[i]){
                        if(a instanceof RequestParam){
                            String paramName= ((RequestParam) a).value();
                            if(!"".equals(paramName.trim())){
                                String value = Arrays.toString(params.get(paramName)).replaceAll("\\[|\\]","").replaceAll("\\s",",");
                                paramValues[i]=value;
                            }
                        }
                    }
                }
            }
        }
        String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        method.invoke(ioc.get(beanName),new Object[]{req,resp,params.get("name")[0]});
    }

    /**
     * 所有的初始化核心方法
     */
    @Override
    public void init(ServletConfig config) throws ServletException {

        //加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        //扫描相关的类
        doScanner(contextConfig.getProperty("scanPackage"));

        //初始化扫描到的类，并且将它们放入IOC容器中
        doInstance();

        //完成依赖注入
        doAutowired();

        //初始化HandlerMapping
        initHandlerMapping();

        System.out.println("Spring Framework is init");

    }

    /**
     * 加载配置文件
     */
    private void doLoadConfig(String contextConfigLocation) {
        //直接通过类路径找到了Spring主配置文件所在的路径
        //并且将其读取出来放到properties对象中
        //相当于将scanPackage=code保存到内存中
        try (InputStream fis = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation)) {
            contextConfig.load(fis);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 扫描相关的类
     */
    private void doScanner(String scanPackage) {
        //scanPackage = code,存放的是包路径
        //转换为文件路径，实际上就是把.替换为/
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File classPath = new File(url.getFile());
        for (File file : classPath.listFiles()) {
            if (file.isDirectory()) {
                doScanner(scanPackage + "." + file.getName());
            } else {
                if (!file.getName().endsWith(".class")) {
                    continue;
                }
                String clazzName = (scanPackage + "." + file.getName().replace(".class", ""));
                classNames.add(clazzName);
            }
        }
    }

    /**
     * 工厂模式的具体体现-
     */
    private void doInstance() {
        //初始化，为DI做准备
        if (classNames.isEmpty()) {
            return;
        }
        try {
            //加了注解的类才需要初始化
            for (String className : classNames) {
                Class<?> clazz = null;
                clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(Controller.class)) {
                    Object instance = clazz.newInstance();
                    //Spring默认的是类名首字母小写
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName, instance);
                } else if (clazz.isAnnotationPresent(Service.class)) {
                    //自定义beanName
                    String beanName = clazz.getAnnotation(Service.class).value();
                    if ("".equals(beanName.trim())) {
                        beanName = toLowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);
                    //根据类型自动赋值，这目前是投机取巧的一种方式
                    for (Class<?> i : clazz.getInterfaces()) {
                        if (ioc.containsKey(i.getName())) {
                            throw new Exception("The '" + i.getName() + "' is exists!");
                        }
                        //把接口的类型直接当成key
                        ioc.put(i.getName(), instance);
                    }
                } else {
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 自动进行依赖注入
     * */
    private void doAutowired(){
        if (ioc.isEmpty()) {
            return;
        }
        for(Map.Entry<String,Object> entry:ioc.entrySet()){
            //获取所有的字段，包括private，protected和default类型
            //正常来说，普通的OOP编程只能获得public类型的字段
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for(Field field : fields){
                if(!field.isAnnotationPresent(Autowired.class)){
                    continue;
                }
                String beanName = field.getAnnotation(Autowired.class).value().trim();
                if("".equals(beanName)){
                    beanName=field.getType().getName();
                }
                //如果是public以外的类型，只要是假了@Autowired的注解都要强制赋值
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 初始化url和method一对一的关系
     * */
    private void initHandlerMapping(){
        if(ioc.isEmpty()){
            return;
        }
        for (Map.Entry<String,Object> entry : ioc.entrySet()){
            Class<?> clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(Controller.class)){
                continue;
            }
            //保存写在类上面的@RequestMapping("/demo")
            String baseUrl="";
            if(clazz.isAnnotationPresent(RequestMapping.class)){
                baseUrl = clazz.getAnnotation(RequestMapping.class).value();
            }

            //默认获取所有的public类型的方法
            for(Method method : clazz.getMethods()){
                if(!method.isAnnotationPresent(RequestMapping.class)){
                    continue;
                }
                RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                String url = ("/" + baseUrl + "/" + requestMapping.value()).replaceAll("/+","/");
                handlerMapping.put(url,method);
                System.out.println("Mapper :" + url + "," + method);
            }
        }
    }

    private String toLowerFirstCase(String str){
        char[] chars = str.toCharArray();
        if(chars[0] >= 'A' && chars[0] <= 'Z'){
            chars[0] += 32;
        }
        return String.valueOf(chars);
    }


}
