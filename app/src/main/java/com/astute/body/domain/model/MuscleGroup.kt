package com.astute.body.domain.model

enum class MuscleGroup(
    val displayName: String,
    val datasetMuscles: Set<String>,
    val classification: Classification
) {
    CHEST("Chest", setOf("chest", "triceps"), Classification.MAJOR_PUSH),
    BACK("Back", setOf("lats", "middle back", "lower back", "traps"), Classification.MAJOR_PULL),
    SHOULDERS("Shoulders", setOf("shoulders", "neck"), Classification.MAJOR_PUSH),
    ARMS("Arms", setOf("biceps", "triceps", "forearms"), Classification.MINOR),
    LEGS_PUSH("Legs (Push)", setOf("quadriceps", "glutes", "abductors", "calves"), Classification.MAJOR_PUSH),
    LEGS_PULL("Legs (Pull)", setOf("hamstrings", "adductors", "glutes"), Classification.MAJOR_PULL),
    CORE("Core", setOf("abdominals"), Classification.MAJOR);

    enum class Classification { MAJOR_PUSH, MAJOR_PULL, MAJOR, MINOR }

    companion object {
        fun fromDisplayName(name: String): MuscleGroup? =
            entries.find { it.displayName == name }

        fun sharedMuscleRatio(candidate: MuscleGroup, selected: List<MuscleGroup>): Double {
            if (selected.isEmpty()) return 0.0
            val selectedMuscles = selected.flatMap { it.datasetMuscles }.toSet()
            val shared = candidate.datasetMuscles.intersect(selectedMuscles)
            return if (candidate.datasetMuscles.isEmpty()) 0.0
            else shared.size.toDouble() / candidate.datasetMuscles.size
        }
    }
}
