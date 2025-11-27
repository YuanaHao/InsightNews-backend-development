package com.sosd.insightnews.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sosd.insightnews.context.UserContext;
import com.sosd.insightnews.converter.PermissionConverter;
import com.sosd.insightnews.converter.RoleConverter;
import com.sosd.insightnews.converter.UserConverter;
import com.sosd.insightnews.dao.entity.*;
import com.sosd.insightnews.dao.mapper.*;
import com.sosd.insightnews.domain.RoleDo;
import com.sosd.insightnews.domain.UserDo;
import com.sosd.insightnews.dto.LoginDTO;
import com.sosd.insightnews.email.EmailService;
import com.sosd.insightnews.exception.http.BadRequestException;
import com.sosd.insightnews.service.RoleService;
import com.sosd.insightnews.service.UserService;
import com.sosd.insightnews.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.hutool.core.util.RandomUtil;
import com.sosd.insightnews.constant.RedisConstants;
import com.sosd.insightnews.email.builder.CodeEmailBuilder;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.sosd.insightnews.constant.RedisConstants.VERIFY_CODE;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private EmailService emailService;

    @Autowired
    private RoleService roleService;

    @Override
    public UserDo getUserById(String id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return null;
        }
        UserDo domain = UserConverter.e2do(user);

        List<Role> rolesByUserId = getRolesByUserId(id);
        if (rolesByUserId == null) {
            return domain;
        }
        domain.setRoles(RoleConverter.role2RoleDo(rolesByUserId));

        List<String> roleIds = domain.getRoles().stream().map(RoleDo::getRoleid).toList();
        List<Permission> permissionsByRoleIds = getPermissionsByRoleIds(roleIds);
        if (permissionsByRoleIds == null) {
            return domain;
        }
        domain.setPermissions(PermissionConverter.permission2PermissionDo(permissionsByRoleIds));
        return domain;
    }

    @Override
    @Transactional
    public void updateInfo(UserDo domain) {
        log.info("User[id={}] update domain={}", domain.getId(), domain);
        User e = UserConverter.do2e(domain);
        userMapper.update(e, prepareUpdate(e));
    }

    @Override
    public void logout() {
        UserContext.clear();
    }

    @Override
    @Transactional
    public void delete(String id) {
        userMapper.deleteById(id);
        List<Role> roles = getRolesByUserId(id);
        if (roles == null) {
            return;
        }
        //删除角色和权限
        for (Role role : roles) {
            roleService.deleteUserRoleByRoleId(role.getRoleid());
        }
    }

    // only update not null column
    private LambdaUpdateWrapper<User> prepareUpdate(User e) {
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
        wrapper.set(e.getName() != null, User::getName, e.getName())
                .set(e.getGender() != null, User::getGender, e.getGender())
                .set(e.getRegion()!= null, User::getRegion, e.getRegion())
                .set(e.getProfile()!= null, User::getProfile, e.getProfile())
                .set(e.getOpenId() != null, User::getOpenId, e.getOpenId())
                .set(e.getAvatar() != null, User::getAvatar, e.getAvatar())
                .eq(User::getId, e.getId());
        return wrapper;
    }

    private List<Role> getRolesByUserId(String userId) {
        LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(UserRole::getRoleid).eq(UserRole::getUserid, userId);
        List<String> roleIds = userRoleMapper.selectObjs(wrapper).stream().map(String::valueOf).toList();
        if (roleIds.isEmpty()) {
            log.info("No user roles found for user ID: {}", userId);
            return null;
        }
        LambdaQueryWrapper<Role> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Role::getRoleid, roleIds);
        return roleMapper.selectList(queryWrapper);
    }

    private List<Permission> getPermissionsByRoleIds(List<String> roleIds) {
        LambdaQueryWrapper<RolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(RolePermission::getRoleid, roleIds);
        List<RolePermission> rolePermissions = rolePermissionMapper.selectList(wrapper);
        if (rolePermissions == null || rolePermissions.isEmpty()) {
            return null;
        }
        List<Integer> permissionIds = rolePermissions.stream().map(RolePermission::getPermissionid).toList();
        return permissionMapper.selectBatchIds(permissionIds);
    }


    @Override
    public void sendEmailCode(String email) {
        // 1. 简单的正则校验
        if (!email.matches("^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$")) {
            throw new BadRequestException("邮箱格式错误");
        }

        // 2. 生成6位验证码
        String code = RandomUtil.randomNumbers(6);

        // 3. 存入 Redis，设置过期时间
        stringRedisTemplate.opsForValue().set(
                RedisConstants.VERIFY_CODE_EMAIL + email, 
                code, 
                RedisConstants.VERIFY_CODE_TTL, 
                TimeUnit.MINUTES
        );

        // 4. 发送邮件
        try {
            CodeEmailBuilder builder = new CodeEmailBuilder(code);
            emailService.send(builder.build(email));
        } catch (Exception e) {
            e.printStackTrace();
            throw new BadRequestException("邮件发送失败，请检查邮箱设置");
        }
    }

    /**
     * 邮箱登录
     */
    @Override
    @Transactional
    public String login(LoginDTO loginDTO) {
        String email = loginDTO.getEmail();
        String code = loginDTO.getCode();

        // 1. 校验验证码
        verifyCode(email, code);

        // 2. 查询用户 (注意：这里假设新用户用 email 作为查询条件，或者 id 就是 email)
        // 为了兼容性，这里建议查询 email 字段
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, email);
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new BadRequestException("用户不存在，请先注册", email);
        }

        UserDo userDo = UserConverter.e2do(user);
        return JwtUtil.createTokenByUserId(userDo.getId());
    }

    /**
     * 邮箱注册
     */
    @Override
    @Transactional
    public String register(LoginDTO loginDTO) {
        String email = loginDTO.getEmail();
        String code = loginDTO.getCode();

        // 1. 校验验证码
        verifyCode(email, code);

        // 2. 检查邮箱是否已存在
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, email);
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BadRequestException("该邮箱已注册，请直接登录", email);
        }

        // 3. 创建新用户
        User newUser = new User();
        // 使用 email 作为主键 ID，或者生成 UUID，这里沿用你之前的逻辑：用账号做ID
        newUser.setId(email); 
        newUser.setEmail(email);
        newUser.setPhone("未绑定"); // 手机号暂时置空或设为默认
        newUser.setName("用户" + RandomUtil.randomString(4));
        newUser.setAvatar("https://insightnews.oss-cn-hangzhou.aliyuncs.com/WechatIMG447.jpg");
        newUser.setRegion("未知");
        
        userMapper.insert(newUser);

        // 4. 初始化角色
        roleService.assignUserToUsers(newUser.getId());

        return JwtUtil.createTokenByUserId(newUser.getId());
    }

    // 抽取通用的验证码校验逻辑
    private void verifyCode(String email, String code) {
        String redisCode = stringRedisTemplate.opsForValue().get(RedisConstants.VERIFY_CODE_EMAIL + email);
        if (redisCode == null) {
            throw new BadRequestException("验证码已过期或未发送");
        }
        if (!redisCode.equals(code)) {
            throw new BadRequestException("验证码错误");
        }
        // 验证成功后删除验证码，防止重复使用
        stringRedisTemplate.delete(RedisConstants.VERIFY_CODE_EMAIL + email);
    }
}
