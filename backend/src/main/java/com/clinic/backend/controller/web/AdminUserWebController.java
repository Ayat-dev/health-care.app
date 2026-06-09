package com.clinic.backend.controller.web;

import com.clinic.backend.dto.UserDto;
import com.clinic.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserWebController {

    private final UserService userService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.listActive());
        return "admin/users/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("user", new UserDto());
        model.addAttribute("roles", userService.assignableRoles());
        return "admin/users/form";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute("user") UserDto dto, Model model) {
        try {
            userService.create(dto);
            return "redirect:/admin/users";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("roles", userService.assignableRoles());
            return "admin/users/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("user", userService.toDto(userService.getById(id)));
        model.addAttribute("roles", userService.assignableRoles());
        return "admin/users/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @ModelAttribute("user") UserDto dto, Model model) {
        try {
            userService.update(id, dto);
            return "redirect:/admin/users";
        } catch (IllegalArgumentException e) {
            dto.setId(id);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("roles", userService.assignableRoles());
            return "admin/users/form";
        }
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes ra) {
        try {
            userService.toggleActive(id);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            userService.delete(id);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }
}
