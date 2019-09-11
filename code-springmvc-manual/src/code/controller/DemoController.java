package code.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import code.annotation.Autowired;
import code.annotation.Controller;
import code.annotation.RequestMapping;
import code.annotation.RequestParam;
import java.io.IOException;
import code.service.DemoService;

/**
 * @author 47 1
 */
@Controller
@RequestMapping("/demo")
public class DemoController {

    @Autowired
    private DemoService demoService;

    @RequestMapping("/query")
    public void query(HttpServletRequest request, HttpServletResponse response,@RequestParam("name") String name){
        String result = demoService.get(name);
        try {
            response.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @RequestMapping("/add")
    public void add(HttpServletRequest request, HttpServletResponse response,@RequestParam("a") Integer a,@RequestParam("b") Integer b){
        try {
            response.getWriter().write(a+"+"+b+"="+(a+b));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @RequestMapping("/remove")
    public void remove(HttpServletRequest request, HttpServletResponse response,@RequestParam("id") String id){

    }


}
