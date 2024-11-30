package com.kaige.service.impl;

import com.kaige.entity.User;
import com.kaige.entity.UserFetcher;
import com.kaige.entity.UserTable;
import com.kaige.service.UserService;
import com.kaige.utils.HashUtils;
import lombok.extern.slf4j.Slf4j;
import org.babyfish.jimmer.sql.JSqlClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserServiceImpl implements UserService , UserDetailsService {
    private JSqlClient jSqlClient;
    public UserServiceImpl(JSqlClient jSqlClient) {
        this.jSqlClient = jSqlClient;
    }


    /**
     * 实现spring security 验证
     * @param username
     * @return
     * @throws UsernameNotFoundException
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserTable userTable = UserTable.$;
        User user = jSqlClient.createQuery(userTable)
                .where(userTable.username().eq(username))
                .select(userTable.fetch(
                        UserFetcher.$.allTableFields()
                ))
                .fetchOneOrNull();
        if (user == null) {
            throw new UsernameNotFoundException("用户名不存在");
        }
        return (UserDetails) user;
    }
//    @Override
//    public User findByUsernameAndPassword(String username, String password) {
//
//        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);
//        Authentication authenticate = authenticationManager.authenticate(authenticationToken);
//        Object principal = authenticate.getPrincipal();
//        User user = (User) principal;
//        if (user == null) {
//            throw new UsernameNotFoundException("用户名不存在");
//        }
//        if (!HashUtils.matchBC(password,user.password())){
//            throw new UsernameNotFoundException("密码错误");
//        }
//        return user;
//    }

   @Override
    public User findByUsernameAndPassword(String username, String password) {
        UserTable userTable = UserTable.$;
        User user = jSqlClient.createQuery(userTable)
                 .where(userTable.username().eq(username))
                .select(userTable.fetch(
                        UserFetcher.$.allTableFields()
                ))
                .fetchOneOrNull();
        if (user == null) {
           throw new UsernameNotFoundException("用户名不存在");
        }
        if (!HashUtils.matchBC(password,user.password())){
            throw new UsernameNotFoundException("密码错误");
        }
        return user;
    }


}