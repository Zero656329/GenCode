package com.gencode.system.user.service;

import com.gencode.common.result.PageResult;
import com.gencode.system.user.dto.UserBody;
import com.gencode.system.user.dto.UserQuery;
import com.gencode.system.user.dto.UserVO;

/**
 * 用户服务
 */
public interface UserService {

    PageResult<UserVO> page(UserQuery query);

    UserVO detail(Long id);

    void create(UserBody body);

    void update(UserBody body);

    void delete(Long id);

    void updateStatus(Long id, Integer status);

    void resetPassword(Long id, String password);
}
