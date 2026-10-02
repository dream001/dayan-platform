package com.dayan.platform.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dayan.platform.model.UserAccount;
import com.dayan.platform.repository.mapper.UserAccountMapper;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class PlatformUserDetailsService implements UserDetailsService {

    private final UserAccountMapper userAccountMapper;

    public PlatformUserDetailsService(UserAccountMapper userAccountMapper) {
        this.userAccountMapper = userAccountMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        UserAccount user = userAccountMapper.selectOne(
                Wrappers.<UserAccount>lambdaQuery()
                        .eq(UserAccount::getUsername, username.trim().toLowerCase(Locale.ROOT))
        );
        return toPrincipal(user);
    }

    public PlatformUserPrincipal loadById(long userId) {
        return toPrincipal(userAccountMapper.selectById(userId));
    }

    private PlatformUserPrincipal toPrincipal(UserAccount user) {
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }
        return PlatformUserPrincipal.from(
                user,
                userAccountMapper.selectPermissionCodes(user.getId())
        );
    }
}
