package com.clinic.backend.controller.web;

import com.clinic.backend.department.DepartmentService;
import com.clinic.backend.dto.DepartmentDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/departments")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class DepartmentWebController {

    private final DepartmentService departmentService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("departments", departmentService.listAll());
        return "admin/departments/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("department", new DepartmentDto());
        return "admin/departments/form";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute("department") DepartmentDto dto, Model model) {
        try {
            departmentService.create(dto);
            return "redirect:/admin/departments";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "admin/departments/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("department", departmentService.toDto(departmentService.getById(id)));
        return "admin/departments/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @ModelAttribute("department") DepartmentDto dto, Model model) {
        try {
            departmentService.update(id, dto);
            return "redirect:/admin/departments";
        } catch (IllegalArgumentException e) {
            dto.setId(id);
            model.addAttribute("error", e.getMessage());
            return "admin/departments/form";
        }
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes ra) {
        try {
            departmentService.toggleActive(id);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/departments";
    }
}
