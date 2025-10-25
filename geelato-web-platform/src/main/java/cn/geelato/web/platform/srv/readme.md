该srv包作为服务所在，请遵循如下规范：
1、Controller类直接在包下面，无需创建rest包；
2、方法统一采用ApiPagedResult<?>返回，其中？为VO，命名为DictIteVO
3、无需增加service这一层调用，避免“污水池反”。
4、