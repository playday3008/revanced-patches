package app.revanced.patches.gamehub.misc.login

import app.revanced.patcher.fingerprint

private const val USER_MANAGER_CLASS = "Lcom/xj/common/user/UserManager;"

internal val getAvatarFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == USER_MANAGER_CLASS && method.name == "getAvatar"
    }
}

internal val getNicknameFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == USER_MANAGER_CLASS && method.name == "getNickname"
    }
}

internal val getTokenFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == USER_MANAGER_CLASS && method.name == "getToken"
    }
}

internal val getUidFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == USER_MANAGER_CLASS && method.name == "getUid"
    }
}

internal val isLoginFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == USER_MANAGER_CLASS && method.name == "isLogin"
    }
}
