/**
 * @name 更改用户信息数据
 * @description
 * @param param
 * @returns {string}
 */
function welcome(name) {
    var str = new Array();
    str.append("Hello");
    str.append(name);
    str.append("!");
    return str.join(" ");
}

/**
 * @name 更新用户信息
 * @param user 待修改的用户信息
 * @returns {*}
 */
function updateUser(user) {
    user.setAge(user.getAge() + 1);
    return user;
}

