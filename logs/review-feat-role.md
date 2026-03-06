# 角色管理模块代码审查报告

> **审查时间：** 2026-03-06  
> **审查人：** 王大将军（资深代码审查工程师）  
> **模块：** 角色管理（feat-role）  
> **技术栈：** Spring Boot + MyBatis-Plus + Lombok

---

## 1. SysRole.java（实体类）

**问题列表：**

- `[低]` 字段缺少注释说明。`status`、`deleted` 等字段含义不明确，未使用 `@ApiModelProperty` 或 Javadoc 说明字段语义（如 status：0-禁用 1-启用）。
- `[中]` `status` 字段使用裸 `Integer` 类型，没有通过枚举或常量约束合法值，调用方可以传入任意整数（如 -1、999），缺乏值域约束。
- `[低]` 实体类直接暴露 `deleted` 字段。逻辑删除字段不应对外展示，建议加 `@JsonIgnore` 或在序列化时排除。
- `[低]` 类未加 `@EqualsAndHashCode(callSuper = false)` 标注，Lombok `@Data` 生成的 `equals/hashCode` 在继承场景下可能产生隐患（虽当前未继承，但应养成习惯）。
- `[低]` 缺少 Swagger/OpenAPI 注解（如 `@Schema`），接口文档无法自动生成字段说明。

**改进建议：**

```java
// 1. 为每个字段添加注释
/** 角色状态：0-禁用，1-启用 */
private Integer status;

// 2. 用枚举替代裸整数（推荐）
public enum StatusEnum {
    DISABLED(0), ENABLED(1);
    // ...
}

// 3. 逻辑删除字段不对外暴露
@TableLogic
@JsonIgnore
private Integer deleted;

// 4. 添加 Swagger 注解
@Schema(description = "角色名称")
private String roleName;
```

---

## 2. SysRoleMapper.java（数据访问层）

**问题列表：**

- `[中]` Mapper 接口为空，完全依赖 MyBatis-Plus 自动生成的 SQL，尚可接受，但缺少注释说明设计意图（是否有意为空、是否有自定义 XML 映射文件）。
- `[低]` 没有对应的 XML 映射文件（`SysRoleMapper.xml`）。虽然当前功能简单，一旦需要联表查询（如角色关联权限列表），代码扩展时无路可走，应提前规划目录结构。
- `[低]` `@Mapper` 注解与启动类的 `@MapperScan` 可能重复配置，建议统一选择一种扫描方式。

**改进建议：**

```java
/**
 * 角色数据访问层
 * 当前依赖 MyBatis-Plus BaseMapper 自动实现 CRUD；
 * 复杂查询（如关联权限）请在 SysRoleMapper.xml 中扩展。
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
    // 预留：联表查询角色及其关联菜单
    // List<RoleWithMenuVO> selectRoleWithMenus(@Param("roleId") Long roleId);
}
```

---

## 3. SysRoleService.java（服务接口）

**问题列表：**

- `[中]` 接口只暴露了 `listRoles` 一个自定义方法，缺少以下业务方法签名：
  - `saveRole(SysRole role)` —— 应包含重复 `roleCode` 校验逻辑
  - `updateRole(SysRole role)` —— 应有更新校验
  - `deleteRole(Long id)` —— 应有级联关系检查（如角色是否已分配给用户）
- `[低]` 接口方法无 Javadoc 注释，调用方无法理解参数含义和返回值语义。
- `[低]` 分页能力缺失。`listRoles` 返回 `List<SysRole>`，在角色数量较大时存在全量返回风险，应支持 `IPage<SysRole>` 分页返回。

**改进建议：**

```java
public interface SysRoleService extends IService<SysRole> {

    /**
     * 分页查询角色列表
     * @param roleName 角色名称（模糊匹配，可为空）
     * @param page     分页参数
     */
    IPage<SysRole> listRoles(String roleName, Page<SysRole> page);

    /**
     * 新增角色（含 roleCode 唯一性校验）
     */
    void saveRole(SysRole role);

    /**
     * 删除角色（含用户关联检查）
     */
    void deleteRole(Long id);
}
```

---

## 4. SysRoleServiceImpl.java（服务实现类）

**问题列表：**

- `[高]` **缺少 `roleCode` 唯一性校验。** 在 `listRoles` 以外没有 `save` 的重写，调用 `IService.save()` 时可能插入重复的 `roleCode`，导致数据一致性问题（数据库层面若没有唯一索引则更严重）。
- `[高]` **删除前未校验角色是否已分配给用户。** 直接 `removeById` 会产生孤儿数据（`sys_user_role` 关联表中该角色的记录未清理），导致逻辑错误。
- `[中]` **`listRoles` 无分页，存在性能隐患。** 全量返回 `List<SysRole>`，当数据量增大时（如数万条角色），会造成内存溢出或接口超时。
- `[中]` **缺少事务注解。** 涉及多步写操作时（如新增角色 + 初始化默认权限），未使用 `@Transactional`，存在数据不一致风险。
- `[低]` `wrapper.orderByAsc(SysRole::getId)` 按主键排序，语义上应改为按 `createTime` 倒序，更符合"最新的在前"的业务习惯。

**改进建议：**

```java
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole>
        implements SysRoleService {

    private final SysUserRoleMapper sysUserRoleMapper; // 注入用户-角色关联 Mapper

    @Override
    public IPage<SysRole> listRoles(String roleName, Page<SysRole> page) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(roleName)) {
            wrapper.like(SysRole::getRoleName, roleName);
        }
        wrapper.orderByDesc(SysRole::getCreateTime);
        return this.page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRole(SysRole role) {
        // roleCode 唯一性校验
        long count = this.count(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, role.getRoleCode()));
        if (count > 0) {
            throw new BusinessException("角色编码已存在：" + role.getRoleCode());
        }
        this.save(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        // 校验是否有用户绑定该角色
        long userCount = sysUserRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        if (userCount > 0) {
            throw new BusinessException("该角色已分配给 " + userCount + " 个用户，无法删除");
        }
        this.removeById(id);
    }
}
```

---

## 5. RoleController.java（控制层）

**问题列表：**

- `[高]` **接口完全无鉴权/权限注解。** 所有接口（增删改查）均可在未登录状态下访问，缺少 `@PreAuthorize`、`@RequiresPermissions` 或 Spring Security 方法级权限控制。删除角色这种高危操作尤其需要权限保护。
- `[高]` **新增/更新接口缺少参数校验（`@Valid`/`@Validated`）。** `add` 和 `update` 方法直接将 `@RequestBody SysRole role` 传入服务层，没有任何 Bean Validation，`roleName`、`roleCode` 等必填字段可以为空或超长字段可以填入。
- `[高]` **`update` 方法缺少角色存在性校验。** `PUT /{id}` 直接调用 `updateById`，若 id 不存在则静默失败（MyBatis-Plus 返回 false 但代码忽略了返回值），调用方误以为更新成功。
- `[中]` **`delete` 方法缺少角色存在性校验。** `DELETE /{id}` 直接调用 `removeById`，id 不存在时也返回 `Result.success()`，不符合 RESTful 语义。
- `[中]` **接口缺乏分页支持。** `GET /api/roles` 全量返回，大数据量下存在性能风险（见 Service 层问题）。
- `[中]` **缺少操作日志记录。** 对角色的增删改操作属于敏感操作，应记录操作人、操作时间、操作内容（如通过 AOP 切面实现）。
- `[低]` **控制器缺少类级别和方法级别的注释。** 接口用途、入参含义、出参格式均未说明。
- `[低]` **缺少 Swagger 注解**（`@Tag`、`@Operation`），接口文档自动生成支持不足。

**改进建议：**

```java
@Tag(name = "角色管理")
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")  // 类级别：至少需要登录
public class RoleController {

    private final SysRoleService sysRoleService;

    @Operation(summary = "分页查询角色列表")
    @GetMapping
    @PreAuthorize("hasAuthority('sys:role:list')")
    public Result<IPage<SysRole>> list(
            @RequestParam(required = false) String roleName,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(sysRoleService.listRoles(roleName, new Page<>(pageNum, pageSize)));
    }

    @Operation(summary = "新增角色")
    @PostMapping
    @PreAuthorize("hasAuthority('sys:role:add')")
    public Result<Void> add(@Validated @RequestBody SysRole role) {
        sysRoleService.saveRole(role);
        return Result.success();
    }

    @Operation(summary = "修改角色")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:role:edit')")
    public Result<Void> update(@PathVariable Long id,
                               @Validated @RequestBody SysRole role) {
        SysRole existing = sysRoleService.getById(id);
        if (existing == null) return Result.error(404, "角色不存在");
        role.setId(id);
        sysRoleService.updateById(role);
        return Result.success();
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:role:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        sysRoleService.deleteRole(id);  // 内含关联检查
        return Result.success();
    }
}
```

同时需要在 `SysRole` 实体上添加 JSR-303 注解：

```java
@NotBlank(message = "角色名称不能为空")
@Length(max = 50, message = "角色名称不能超过50个字符")
private String roleName;

@NotBlank(message = "角色编码不能为空")
@Pattern(regexp = "^[A-Z_]+$", message = "角色编码只能包含大写字母和下划线")
@Length(max = 100)
private String roleCode;
```

---

## 总体评分与总结

### 📊 总体评分：**42 / 100**

| 维度 | 得分 | 说明 |
|------|------|------|
| 代码规范 | 12/20 | 结构清晰，但注释严重缺失，Swagger/文档注解为零 |
| 安全性 | 4/30 | **接口无鉴权、无参数校验，是最严重的问题** |
| 性能 | 8/20 | 全量查询无分页，潜在风险较大 |
| 逻辑完整性 | 18/30 | 缺少唯一性校验、关联检查、更新存在性校验，逻辑不完整 |

---

### 🔴 高危问题（必须修复，上线前 Blocker）

1. **接口无任何权限控制** —— 删除、修改角色等高危操作完全暴露，必须加 `@PreAuthorize`
2. **新增/修改接口无参数校验** —— `roleName`、`roleCode` 可为空或任意字符串，存在数据污染风险
3. **新增角色无 `roleCode` 唯一性校验** —— 可造成重复数据，影响后续权限判断逻辑
4. **删除角色无关联用户检查** —— 直接删除会导致用户-角色关联表数据悬空（孤儿数据）

### 🟡 中等问题（本迭代内修复）

5. **全量返回列表，无分页** —— 改为 `IPage` 分页接口
6. **更新/删除前缺少存在性校验** —— 需要先 `getById` 确认目标存在
7. **缺少操作日志** —— 高危操作（增删改）需要留审计日志

### 🟢 低优化建议（后续迭代）

8. 实体类字段添加注释和 Swagger 注解
9. `status` 字段改为枚举约束
10. `deleted` 字段加 `@JsonIgnore`
11. 提前规划 `SysRoleMapper.xml` 目录结构
12. Service 接口方法补全 Javadoc

---

> **总结：** 该模块代码框架搭建合理，基础 CRUD 功能实现清晰，但整体处于"能跑但不安全、不健壮"的阶段。最核心的问题是**安全层完全缺失**——无鉴权、无校验，这在任何生产环境中都是不可接受的。建议在进入测试环境前，优先解决全部高危问题，再处理中等问题。
