package com.exam.exam_system.service.impl;

import com.exam.exam_system.entity.SysMenu;
import com.exam.exam_system.mapper.SysMenuMapper;
import com.exam.exam_system.mapper.SysRoleMenuMapper;
import com.exam.exam_system.service.SysMenuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单服务实现
 *
 * <p>核心是树构建逻辑：DB flat list → 按 parentId 组装成树。
 * <p>所有返回菜单树的方法都走 {@link #buildTree} 统一构建。
 */
@Service
public class SysMenuServiceImpl implements SysMenuService {

    @Autowired
    private SysMenuMapper sysMenuMapper;

    @Autowired
    private SysRoleMenuMapper sysRoleMenuMapper;

    @Override
    public List<SysMenu> listAllTree() {
        return buildTree(sysMenuMapper.selectAll());
    }

    @Override
    public List<SysMenu> listTreeByLayout(String layout) {
        return buildTree(sysMenuMapper.selectAllByLayout(layout));
    }

    @Override
    public List<SysMenu> listTreeByRoleId(Long roleId) {
        return buildTree(sysMenuMapper.selectByRoleId(roleId));
    }

    @Override
    public List<Long> getMenuIdsByRoleId(Long roleId) {
        return sysRoleMenuMapper.selectMenuIdsByRoleId(roleId);
    }

    @Override
    public SysMenu getById(Long id) {
        return sysMenuMapper.selectById(id);
    }

    @Override
    public Long save(SysMenu menu) {
        sysMenuMapper.insert(menu);
        return menu.getId();
    }

    @Override
    public void update(SysMenu menu) {
        sysMenuMapper.update(menu);
    }

    @Override
    public void delete(Long id) {
        sysMenuMapper.delete(id);
    }

    /**
     * 把 flat list 组装成树。
     *
     * <p>算法：
     * <ol>
     *   <li>用 Map 按 id 索引所有节点（每个节点的 children 已初始化为空 list）</li>
     *   <li>遍历每个节点，找它的 parentId 对应的父节点，把自己加到父节点的 children</li>
     *   <li>parentId=0 的就是顶级，留在结果 list 里</li>
     *   <li>每个节点根据 children 是否非空设置 hasChildren</li>
     * </ol>
     *
     * <p>假设输入已按 sort_order 升序排好（mapper SQL 已 ORDER BY），
     * 这样 children 内部顺序也稳定。
     */
    private List<SysMenu> buildTree(List<SysMenu> flatList) {
        if (flatList == null || flatList.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, SysMenu> idIndex = new HashMap<>();
        for (SysMenu m : flatList) {
            // 防御性：每次构建都重新初始化 children
            m.setChildren(new ArrayList<>());
            idIndex.put(m.getId(), m);
        }
        List<SysMenu> roots = new ArrayList<>();
        for (SysMenu m : flatList) {
            Long pid = m.getParentId();
            if (pid == null || pid == 0L) {
                roots.add(m);
            } else {
                SysMenu parent = idIndex.get(pid);
                if (parent != null) {
                    parent.getChildren().add(m);
                } else {
                    // 找不到父节点：当成顶级节点处理，避免菜单丢失
                    roots.add(m);
                }
            }
        }
        // 设置 hasChildren 标志，方便前端判断渲染为 el-sub-menu 还是 el-menu-item
        for (SysMenu m : idIndex.values()) {
            m.setHasChildren(m.getChildren() != null && !m.getChildren().isEmpty());
        }
        return roots;
    }
}
