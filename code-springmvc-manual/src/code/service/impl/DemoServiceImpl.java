package code.service.impl;

import code.annotation.Service;
import code.service.DemoService;

/**
 * @author 47 1
 */
@Service
public class DemoServiceImpl implements DemoService {
    @Override
    public String get(String name) {
        return "My name is "+name;
    }
}
