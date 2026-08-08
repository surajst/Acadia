package com.concept.console.app;

import com.concept.rewards.app.RewardView;

import java.util.List;

/**
 * Everything the admin console hub renders, as flat views — classrooms, reward
 * inventory, and the three headline counts. No entities reach the template.
 */
public record ConsoleView(List<ClassroomView> classList,
                          List<RewardView> rewardInventoryList,
                          long totalStudents,
                          long totalStaff,
                          long totalClassrooms) {}
