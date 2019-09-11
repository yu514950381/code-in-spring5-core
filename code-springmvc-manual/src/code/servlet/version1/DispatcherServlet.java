package code.servlet.version1;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import code.annotation.Autowired;
import code.annotation.Controller;
import code.annotation.RequestMapping;
import code.annotation.Service;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

/**
 * @author 47 1
 */
public class DispatcherServlet extends HttpServlet {

    private Map<String,Object> mapping = new HashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatcher(req,resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exception "+ Arrays.toString(e.getStackTrace()));
        }
    }


    private void doDispatcher(HttpServletRequest req, HttpServletResponse resp)throws Exception{
        String url = req.getRequestURI();
        if(!this.mapping.containsKey(url)){
            resp.getWriter().write("404 Not Found");
            return;
        }
        //TODO ?????需要看看是为什么
        Method method = (Method)this.mapping.get(url);

        Map<String,String[]> params = req.getParameterMap();
        Object obj = this.mapping.get(method.getDeclaringClass().getName());
        method.invoke(obj,new Object[]{req,resp,params.get("name")[0]});
    }

    /**
     * 所有的初始化核心方法
     * */
    @Override
    public void init(ServletConfig config) throws ServletException {

        try(
                //Class.getResourceAsStream(String path): path 不以’/'开头时默认是从此类所在的包下取资源，以’/'开头则是从ClassPath根下获取。其只是通过path构造一个绝对路径，最终还是由ClassLoader获取资源。
                //
                //Class.getClassLoader.getResourceAsStream(String path): 默认则是从ClassPath根下获取，path不能以’/'开头，最终是由ClassLoader获取资源。
                //
                //ServletContext. getResourceAsStream(String path): 默认从WebAPP根目录下取资源，Tomcat下path是否以’/'开头无所谓，当然这和具体的容器实现有关。
                //
                //Jsp下的application内置对象就是上面的ServletContext的一种实现。
                InputStream is = this.getClass().getClassLoader().getResourceAsStream(config.getInitParameter("contextConfigLocation"))
        ) {
            Properties configContext = new Properties();
            configContext.load(is);

            //获取要扫描的类的根路径
            String scanPackage = configContext.getProperty("scanPackage");
            Map<String,Object> tempMapping= new HashMap<>();

            doScanner(scanPackage);

            //复制一份用来进行遍历
            for (Map.Entry<String, Object> entry : mapping.entrySet()) {
                String a = entry.getKey();
                Object b = entry.getValue();
                tempMapping.put(a, b);
            }

            for (String className : tempMapping.keySet()){
                if(!className.contains(".")){
                    continue;
                }
                Class<?> clazz = Class.forName(className);
                if(clazz.isAnnotationPresent(Controller.class)){
                    mapping.put(className,clazz.newInstance());
                    String baseUrl = "";
                    if(clazz.isAnnotationPresent(RequestMapping.class)){
                        baseUrl=clazz.getAnnotation(RequestMapping.class).value();
                    }
                    Method[] methods = clazz.getMethods();
                    for (Method method:methods){
                        if(!method.isAnnotationPresent(RequestMapping.class)){
                            continue;
                        }
                        RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                        String url = (baseUrl+"/"+requestMapping.value()).replaceAll("/+","/");
                        mapping.put(url,method);
                        System.out.println("Mapped " + url + "," + method);
                    }
                }else if(clazz.isAnnotationPresent(Service.class)){
                    Service service = clazz.getAnnotation(Service.class);
                    String beanName = service.value();
                    if("".equals(beanName)){
                        beanName=clazz.getName();
                    }
                    Object instance = clazz.newInstance();
                    mapping.put(beanName,instance);
                    for(Class<?> i:clazz.getInterfaces()){
                        mapping.put(i.getName(),instance);
                    }
                }
            }
            tempMapping.clear();
            for(Object object:mapping.values()){
                if(object==null){
                    continue;
                }
                Class clazz = object.getClass();
                if(clazz.isAnnotationPresent(Controller.class)){
                    Field[] fields = clazz.getDeclaredFields();
                    for(Field field:fields){
                        if(!field.isAnnotationPresent(Autowired.class)){
                            continue;
                        }
                        String beanName = field.getAnnotation(Autowired.class).value();
                        if("".equals(beanName)){
                            beanName = field.getType().getName();
                        }
                        field.setAccessible(true);
                        field.set(mapping.get(clazz.getName()),mapping.get(beanName));
                    }
                }
            }
        } catch (IOException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        System.out.println("MVC Framework is init");
    }


    private void doScanner(String scanPackage){
        URL url = this.getClass().getClassLoader().getResource("/"+scanPackage.replaceAll("\\.","/"));
        File classDir = new File(url.getFile());
        for(File file: classDir.listFiles()){
            if(file.isDirectory()){
                 doScanner(scanPackage+"."+file.getName());
            }else{
                if(!file.getName().endsWith(".class")){
                    continue;
                }
                String clazzName = (scanPackage+"."+file.getName().replace(".class",""));
                mapping.put(clazzName,null);
            }
        }
    }

}
