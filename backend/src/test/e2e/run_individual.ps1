$tests = @(
    'tests\announcement-flow.spec.js',
    'tests\assign-task.spec.js',
    'tests\mastery_task.spec.js',
    'tests\mobile_responsive.spec.js',
    'tests\parent_child_progress.spec.js',
    'tests\quest-engagement.spec.js',
    'tests\real_user_journey.spec.js',
    'tests\rewards-delivery.spec.js',
    'tests\syllabus_verification.spec.js',
    'tests\teacher_task_creation.spec.js',
    'tests\workflow.spec.js'
)

foreach ($test in $tests) {
    Write-Host "Running $test..."
    npx playwright test $test --reporter=line
    if ($LASTEXITCODE -eq 0) {
        Write-Host "$test PASSED`n"
    } else {
        Write-Host "$test FAILED`n"
    }
}
