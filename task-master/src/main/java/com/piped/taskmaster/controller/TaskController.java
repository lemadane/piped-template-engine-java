package com.piped.taskmaster.controller;

import com.piped.taskmaster.model.Task;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Controller
public class TaskController {

    final List<Task> tasks = new ArrayList<>();

    public TaskController() {
        // Pre-populate with some sample tasks
        tasks.add(new Task(UUID.randomUUID().toString(), "Explore Piped Template Engine features", true));
        tasks.add(new Task(UUID.randomUUID().toString(), "Integrate with Spring Boot MVC", true));
        tasks.add(new Task(UUID.randomUUID().toString(), "Build an interactive task-master todo app", false));
        tasks.add(new Task(UUID.randomUUID().toString(), "Deploy to production", false));
    }

    @GetMapping("/")
    public String index(Model model) {
        populateModel(model);
        model.addAttribute("isHtmx", false);
        return "pages/index";
    }

    @PostMapping("/tasks")
    public String addTask(@RequestParam("title") String title, Model model) {
        if (title != null && !title.trim().isEmpty()) {
            tasks.add(new Task(UUID.randomUUID().toString(), title.trim(), false));
        }
        populateModel(model);
        model.addAttribute("isHtmx", true);
        return "partials/task-list";
    }

    @PostMapping("/tasks/{id}/toggle")
    public String toggleTask(@PathVariable("id") String id, Model model) {
        for (Task task : tasks) {
            if (task.getId().equals(id)) {
                task.setCompleted(!task.isCompleted());
                break;
            }
        }
        populateModel(model);
        model.addAttribute("isHtmx", true);
        return "partials/task-list";
    }

    @DeleteMapping("/tasks/{id}")
    public String deleteTask(@PathVariable("id") String id, Model model) {
        tasks.removeIf(task -> task.getId().equals(id));
        populateModel(model);
        model.addAttribute("isHtmx", true);
        return "partials/task-list";
    }

    void populateModel(Model model) {
        long totalCount = tasks.size();
        long completedCount = tasks.stream()
                .filter(task -> task != null && task.isCompleted())
                .count();
        long activeCount = totalCount - completedCount;
        model.addAttribute("tasks", tasks);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("completedCount", completedCount);
        model.addAttribute("activeCount", activeCount);
    }
}