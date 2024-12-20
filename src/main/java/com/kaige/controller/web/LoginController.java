package com.kaige.controller.web;

import com.kaige.constant.JwtConstant;
import com.kaige.entity.Immutables;
import com.kaige.entity.Result;
import com.kaige.entity.User;
import com.kaige.entity.dto.UserInput;
import com.kaige.service.UserService;
import com.kaige.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;

@RestController
public class LoginController {

    @Autowired
    private UserService userService;

    /**
     * 登录后 签发token
     * @param userInput
     * @return
     */
    @PostMapping("/login")
    public Result login(@RequestBody UserInput userInput){
        User user = userService.findByUsernameAndPassword(userInput.getUsername(), userInput.getPassword());
        if (!"ROLE_ADMIN".equals(user.role())){
            return Result.create(403,"无权限");
        }
        // 这里其实可以用一个 Dto来 封装 就可以用 set/get 方法
        User user1 = Immutables.createUser(user, draft -> {
            draft.setPassword("");
        });
        String jwt = JwtUtils.generateToken(JwtConstant.ADMIN_PREFIX + user.username());
        HashMap<String, Object> map = new HashMap<>(4);
        map.put("user",user1);
        map.put("token",jwt);
        return Result.ok("登录成功findByUsernameAndPassword",map);
    }
}
