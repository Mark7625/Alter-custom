package org.rsmod.content.skills.agility.rooftop

import org.rsmod.content.skills.agility.AgilityAnims
import org.rsmod.content.skills.agility.BalanceStyle
import org.rsmod.content.skills.agility.line
import org.rsmod.content.skills.agility.rooftop.ObstacleMove.Balance
import org.rsmod.content.skills.agility.rooftop.ObstacleMove.Climb
import org.rsmod.content.skills.agility.rooftop.ObstacleMove.Leap
import org.rsmod.content.skills.agility.rooftop.ObstacleMove.Zipline
import org.rsmod.map.CoordGrid

/**
 * Layouts of the nine rooftop courses.
 *
 * Every start tile, landing tile and balance path was read from the game map: the obstacle loc
 * positions and sizes come from the cache, and each tile a player is placed on was checked to be
 * an unblocked roof tile on the level the server actually uses (Canifis' roofs are "bridge"
 * tiles, so its obstacles live on level 2 even though they render on level 3). Movement never
 * relies on the route finder, so players cannot be knocked off a course by collision.
 *
 * Experience values are the wiki's per-obstacle values; the final obstacle's total is split into a
 * small base and a lap bonus that is only paid for an in-order lap.
 */
object RooftopCourses {
    val layouts: List<CourseLayout> =
        listOf(
            draynor(),
            alKharid(),
            varrock(),
            canifis(),
            falador(),
            seers(),
            pollnivneach(),
            rellekka(),
            ardougne(),
        )

    fun layout(course: RooftopCourse): CourseLayout = layouts.first { it.course == course }

    private fun tile(x: Int, z: Int, level: Int): CoordGrid = CoordGrid(x, z, level)

    private fun draynor(): CourseLayout =
        CourseLayout(
            course = RooftopCourse.Draynor,
            obstacles =
                listOf(
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_draynor_wallclimb"),
                        name = "Rough wall",
                        xp = 5.0,
                        start = tile(3103, 3279, 0),
                        move = Climb(tile(3102, 3279, 3)),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_draynor_tightrope_1"),
                        name = "Tightrope",
                        xp = 8.0,
                        start = tile(3099, 3277, 3),
                        move =
                            Balance(
                                line(tile(3099, 3277, 3), tile(3090, 3277, 3)) + tile(3090, 3276, 3)
                            ),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_draynor_tightrope_2"),
                        name = "Tightrope",
                        xp = 7.0,
                        start = tile(3091, 3276, 3),
                        move =
                            Balance(
                                listOf(tile(3092, 3276, 3)) +
                                    line(tile(3092, 3276, 3), tile(3092, 3266, 3))
                            ),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_draynor_wallcrossing"),
                        name = "Narrow wall",
                        xp = 7.0,
                        start = tile(3089, 3265, 3),
                        move =
                            Balance(
                                line(tile(3089, 3265, 3), tile(3089, 3262, 3)) + tile(3088, 3261, 3)
                            ),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_draynor_wallscramble"),
                        name = "Wall",
                        xp = 10.0,
                        start = tile(3088, 3257, 3),
                        move = Leap(tile(3088, 3255, 3), seq = AgilityAnims.JUMP_UP),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_draynor_leapdown"),
                        name = "Gap",
                        xp = 4.0,
                        start = tile(3094, 3255, 3),
                        move = Leap(tile(3096, 3256, 3)),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_draynor_crate"),
                        name = "Crate",
                        xp = 4.0,
                        lapBonusXp = 75.0,
                        start = tile(3101, 3261, 3),
                        move = Leap(tile(3103, 3261, 0), seq = AgilityAnims.JUMP_DOWN),
                    ),
                ),
            markTiles =
                listOf(
                    tile(3100, 3280, 3),
                    tile(3089, 3275, 3),
                    tile(3093, 3266, 3),
                    tile(3088, 3259, 3),
                    tile(3090, 3255, 3),
                    tile(3098, 3259, 3),
                ),
        )

    private fun alKharid(): CourseLayout =
        CourseLayout(
            course = RooftopCourse.AlKharid,
            obstacles =
                listOf(
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_kharid_wallclimb"),
                        name = "Rough wall",
                        xp = 12.0,
                        start = tile(3273, 3195, 0),
                        move = Climb(tile(3273, 3192, 3)),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_kharid_tightrope_1"),
                        name = "Tightrope",
                        xp = 36.0,
                        start = tile(3272, 3182, 3),
                        move = Balance(line(tile(3272, 3182, 3), tile(3272, 3172, 3))),
                        failure = ObstacleFailure(30, tile(3272, 3177, 0), 1, 5),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_kharid_rope_swing"),
                        name = "Cable",
                        xp = 48.0,
                        start = tile(3268, 3166, 3),
                        move = Leap(tile(3283, 3166, 3), seq = AgilityAnims.ROPE_SWING, ticks = 3),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_kharid_slide_side"),
                        name = "Zip line",
                        xp = 48.0,
                        start = tile(3301, 3163, 3),
                        move = Zipline(tile(3315, 3163, 1), ticks = 3),
                        failure = ObstacleFailure(30, tile(3308, 3163, 0), 1, 5),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_kharid_bamboo_tree_top"),
                        name = "Tropical tree",
                        xp = 12.0,
                        start = tile(3318, 3165, 1),
                        move = Leap(tile(3317, 3175, 2), seq = AgilityAnims.ROPE_SWING, ticks = 3),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_kharid_wallclimb_2"),
                        name = "Roof top beams",
                        xp = 6.0,
                        start = tile(3316, 3178, 2),
                        move = Climb(tile(3316, 3180, 3)),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_kharid_tightrope_4"),
                        name = "Tightrope",
                        xp = 18.0,
                        start = tile(3314, 3186, 3),
                        move = Balance(line(tile(3314, 3186, 3), tile(3304, 3186, 3))),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_kharid_leapdown"),
                        name = "Gap",
                        xp = 6.0,
                        lapBonusXp = 30.0,
                        start = tile(3300, 3192, 3),
                        move = Leap(tile(3299, 3194, 0), seq = AgilityAnims.JUMP_DOWN),
                    ),
                ),
            markTiles =
                listOf(
                    tile(3274, 3190, 3),
                    tile(3270, 3170, 3),
                    tile(3290, 3165, 3),
                    tile(3316, 3162, 1),
                    tile(3316, 3176, 2),
                    tile(3316, 3183, 3),
                    tile(3301, 3190, 3),
                ),
        )

    private fun varrock(): CourseLayout =
        CourseLayout(
            course = RooftopCourse.Varrock,
            obstacles =
                listOf(
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_varrock_wallclimb"),
                        name = "Rough wall",
                        xp = 13.5,
                        start = tile(3221, 3414, 0),
                        move = Climb(tile(3219, 3414, 3)),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_varrock_clothesline"),
                        name = "Clothes line",
                        xp = 23.0,
                        start = tile(3214, 3414, 3),
                        move = Balance(line(tile(3214, 3414, 3), tile(3208, 3414, 3))),
                        failure = ObstacleFailure(40, tile(3211, 3414, 0), 3, 8),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_varrock_leaptoruins"),
                        name = "Gap",
                        xp = 19.0,
                        start = tile(3201, 3416, 3),
                        move = Leap(tile(3193, 3416, 1), seq = AgilityAnims.JUMP_DOWN, ticks = 3),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_varrock_wallswing"),
                        name = "Wall",
                        xp = 28.0,
                        start = tile(3193, 3416, 1),
                        move =
                            Balance(
                                listOf(tile(3192, 3415, 3)) +
                                    line(tile(3192, 3415, 3), tile(3192, 3407, 3)) +
                                    tile(3193, 3406, 3)
                            ),
                        failure = ObstacleFailure(40, tile(3191, 3412, 0), 2, 5),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_varrock_wallscramble"),
                        name = "Gap",
                        xp = 10.0,
                        start = tile(3193, 3402, 3),
                        move = Leap(tile(3193, 3398, 3)),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_varrock_leaptobalcony"),
                        name = "Gap",
                        xp = 24.5,
                        start = tile(3208, 3397, 3),
                        move = Leap(tile(3218, 3397, 3), ticks = 3),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_varrock_leapdown"),
                        name = "Gap",
                        xp = 4.5,
                        start = tile(3232, 3402, 3),
                        move = Leap(tile(3236, 3403, 3)),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_varrock_stepuproof"),
                        name = "Ledge",
                        xp = 3.5,
                        start = tile(3236, 3408, 3),
                        move = Leap(tile(3236, 3410, 3), seq = AgilityAnims.JUMP_UP, ticks = 1),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_varrock_finish"),
                        name = "Edge",
                        xp = 7.0,
                        lapBonusXp = 136.7,
                        start = tile(3236, 3415, 3),
                        move = Leap(tile(3236, 3417, 0), seq = AgilityAnims.JUMP_DOWN),
                    ),
                ),
            markTiles =
                listOf(
                    tile(3219, 3417, 3),
                    tile(3208, 3400, 3),
                    tile(3195, 3404, 3),
                    tile(3218, 3399, 3),
                    tile(3225, 3402, 3),
                    tile(3237, 3406, 3),
                    tile(3237, 3413, 3),
                ),
        )

    private fun canifis(): CourseLayout =
        CourseLayout(
            course = RooftopCourse.Canifis,
            obstacles =
                listOf(
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_canifis_start_tree"),
                        name = "Tall tree",
                        xp = 10.0,
                        start = tile(3506, 3488, 0),
                        move = Climb(tile(3506, 3492, 2), seq = AgilityAnims.CLIMB),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_canifis_jump"),
                        name = "Gap",
                        xp = 8.0,
                        start = tile(3505, 3497, 2),
                        move = Leap(tile(3503, 3504, 2)),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_canifis_jump_2"),
                        name = "Gap",
                        xp = 8.0,
                        start = tile(3497, 3505, 2),
                        move = Leap(tile(3491, 3504, 2), ticks = 3),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_canifis_jump_5"),
                        name = "Gap",
                        xp = 10.0,
                        start = tile(3487, 3499, 2),
                        move = Leap(tile(3479, 3499, 3)),
                        failure = ObstacleFailure(65, tile(3482, 3499, 0), 2, 5),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_canifis_jump_3"),
                        name = "Gap",
                        xp = 8.0,
                        start = tile(3478, 3493, 3),
                        move = Leap(tile(3478, 3487, 2)),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_canifis_polevault"),
                        name = "Pole-vault",
                        xp = 10.0,
                        start = tile(3481, 3482, 2),
                        move = Leap(tile(3490, 3476, 3), seq = AgilityAnims.POLE_VAULT, ticks = 3),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_canifis_jump_4"),
                        name = "Gap",
                        xp = 11.0,
                        start = tile(3502, 3476, 3),
                        move = Leap(tile(3510, 3476, 2), ticks = 3),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_canifis_leapdown"),
                        name = "Gap",
                        xp = 10.0,
                        lapBonusXp = 165.0,
                        start = tile(3510, 3482, 2),
                        move = Leap(tile(3510, 3485, 0), seq = AgilityAnims.JUMP_DOWN),
                    ),
                ),
            markTiles =
                listOf(
                    tile(3507, 3495, 2),
                    tile(3499, 3505, 2),
                    tile(3489, 3500, 2),
                    tile(3477, 3495, 3),
                    tile(3480, 3486, 2),
                    tile(3495, 3475, 3),
                    tile(3511, 3479, 2),
                ),
        )

    private fun falador(): CourseLayout =
        CourseLayout(
            course = RooftopCourse.Falador,
            obstacles =
                listOf(
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_falador_wallclimb"),
                        name = "Rough wall",
                        xp = 11.0,
                        start = tile(3036, 3341, 0),
                        move = Climb(tile(3036, 3342, 3)),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_falador_tightrope_1"),
                        name = "Tightrope",
                        xp = 22.0,
                        start = tile(3039, 3343, 3),
                        move = Balance(line(tile(3039, 3343, 3), tile(3044, 3343, 3))),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_falador_handholds_start"),
                        name = "Hand holds",
                        xp = 61.0,
                        start = tile(3050, 3349, 3),
                        move =
                            Balance(
                                line(tile(3050, 3349, 3), tile(3050, 3357, 3)),
                                style = BalanceStyle.Handholds,
                            ),
                        failure = ObstacleFailure(66, tile(3052, 3353, 0), 3, 8),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_falador_gap_1"),
                        name = "Gap",
                        xp = 27.0,
                        start = tile(3048, 3358, 3),
                        move = Leap(tile(3048, 3361, 3)),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_falador_gap_2"),
                        name = "Gap",
                        xp = 26.0,
                        start = tile(3046, 3361, 3),
                        move = Leap(tile(3041, 3361, 3)),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_falador_tightrope_2"),
                        name = "Tightrope",
                        xp = 61.0,
                        start = tile(3035, 3361, 3),
                        move =
                            Balance(
                                listOf(tile(3034, 3361, 3)) +
                                    line(tile(3034, 3361, 3), tile(3028, 3355, 3)) +
                                    tile(3028, 3354, 3)
                            ),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_falador_tightrope_3"),
                        name = "Tightrope",
                        xp = 53.0,
                        start = tile(3027, 3353, 3),
                        move = Balance(line(tile(3027, 3353, 3), tile(3020, 3353, 3))),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_falador_gap_3"),
                        name = "Gap",
                        xp = 30.0,
                        start = tile(3017, 3353, 3),
                        move = Leap(tile(3017, 3349, 3)),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_falador_ledge_1"),
                        name = "Ledge",
                        xp = 14.0,
                        start = tile(3016, 3345, 3),
                        move = Leap(tile(3014, 3345, 3), ticks = 1),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_falador_ledge_2"),
                        name = "Ledge",
                        xp = 13.0,
                        start = tile(3012, 3344, 3),
                        move = Leap(tile(3012, 3342, 3), ticks = 1),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_falador_ledge_3a", "loc.rooftops_falador_ledge_3b"),
                        name = "Ledge",
                        xp = 13.0,
                        start = tile(3013, 3335, 3),
                        move = Leap(tile(3013, 3333, 3), ticks = 1),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_falador_ledge_4"),
                        name = "Ledge",
                        xp = 14.0,
                        start = tile(3017, 3332, 3),
                        move = Leap(tile(3021, 3332, 3)),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_falador_edge"),
                        name = "Edge",
                        xp = 11.0,
                        lapBonusXp = 230.0,
                        start = tile(3024, 3332, 3),
                        move = Leap(tile(3029, 3332, 0), seq = AgilityAnims.JUMP_DOWN),
                    ),
                ),
            markTiles =
                listOf(
                    tile(3038, 3342, 3),
                    tile(3048, 3346, 3),
                    tile(3049, 3357, 3),
                    tile(3038, 3363, 3),
                    tile(3012, 3356, 3),
                    tile(3018, 3348, 3),
                    tile(3010, 3340, 3),
                    tile(3015, 3333, 3),
                    tile(3022, 3334, 3),
                ),
        )

    private fun seers(): CourseLayout =
        CourseLayout(
            course = RooftopCourse.Seers,
            obstacles =
                listOf(
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_seers_wallclimb"),
                        name = "Wall",
                        xp = 45.0,
                        start = tile(2729, 3489, 0),
                        move = Climb(tile(2729, 3490, 3)),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_seers_jump"),
                        name = "Gap",
                        xp = 20.0,
                        start = tile(2721, 3494, 3),
                        move = Leap(tile(2713, 3494, 2), ticks = 3),
                        failure = ObstacleFailure(79, tile(2716, 3494, 0), 2, 6),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_seers_tightrope"),
                        name = "Tightrope",
                        xp = 20.0,
                        start = tile(2710, 3490, 2),
                        move = Balance(line(tile(2710, 3490, 2), tile(2710, 3480, 2))),
                        failure = ObstacleFailure(79, tile(2711, 3485, 0), 2, 6),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_seers_jump_1"),
                        name = "Gap",
                        xp = 35.0,
                        start = tile(2710, 3477, 2),
                        move = Leap(tile(2710, 3472, 3)),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_seers_jump_2"),
                        name = "Gap",
                        xp = 15.0,
                        start = tile(2700, 3470, 3),
                        move = Leap(tile(2700, 3465, 2)),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_seers_leapdown"),
                        name = "Edge",
                        xp = 15.0,
                        lapBonusXp = 420.0,
                        start = tile(2702, 3464, 2),
                        move = Leap(tile(2704, 3464, 0), seq = AgilityAnims.JUMP_DOWN),
                    ),
                ),
            markTiles =
                listOf(
                    tile(2725, 3492, 3),
                    tile(2708, 3494, 2),
                    tile(2713, 3479, 2),
                    tile(2702, 3473, 3),
                    tile(2700, 3462, 2),
                ),
        )

    private fun pollnivneach(): CourseLayout =
        CourseLayout(
            course = RooftopCourse.Pollnivneach,
            obstacles =
                listOf(
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_pollnivneach_basket"),
                        name = "Basket",
                        xp = 10.0,
                        start = tile(3351, 2961, 0),
                        move = Climb(tile(3351, 2964, 1), seq = AgilityAnims.JUMP_UP),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_pollnivneach_marketstall"),
                        name = "Market stall",
                        xp = 45.0,
                        start = tile(3349, 2968, 1),
                        move = Leap(tile(3352, 2973, 1), ticks = 3),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_pollnivneach_hangingbanner"),
                        name = "Banner",
                        xp = 65.0,
                        start = tile(3356, 2976, 1),
                        move = Leap(tile(3360, 2978, 1), seq = AgilityAnims.ROPE_SWING),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_pollnivneach_gap"),
                        name = "Gap",
                        xp = 35.0,
                        start = tile(3362, 2977, 1),
                        move = Leap(tile(3366, 2976, 1)),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_pollnivneach_tree"),
                        name = "Tree",
                        xp = 75.0,
                        start = tile(3367, 2976, 1),
                        move = Leap(tile(3367, 2982, 1), ticks = 3),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_pollnivneach_wallclimb"),
                        name = "Rough wall",
                        xp = 5.0,
                        start = tile(3365, 2982, 1),
                        move = Climb(tile(3365, 2983, 2)),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_pollnivneach_monkeybars_start"),
                        name = "Monkeybars",
                        xp = 55.0,
                        start = tile(3358, 2984, 2),
                        move =
                            Balance(
                                line(tile(3358, 2984, 2), tile(3358, 2992, 2)),
                                style = BalanceStyle.Monkeybars,
                            ),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_pollnivneach_treetop"),
                        name = "Tree",
                        xp = 60.0,
                        start = tile(3359, 2995, 2),
                        move = Leap(tile(3359, 3000, 2), ticks = 3),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_pollnivneach_line"),
                        name = "Drying line",
                        xp = 20.0,
                        lapBonusXp = 520.0,
                        start = tile(3362, 3002, 2),
                        move = Leap(tile(3363, 2998, 0), seq = AgilityAnims.JUMP_DOWN, ticks = 3),
                    ),
                ),
            markTiles =
                listOf(
                    tile(3348, 2966, 1),
                    tile(3354, 2975, 1),
                    tile(3361, 2980, 1),
                    tile(3368, 2975, 1),
                    tile(3357, 2983, 2),
                    tile(3364, 2993, 2),
                    tile(3357, 3002, 2),
                ),
        )

    private fun rellekka(): CourseLayout =
        CourseLayout(
            course = RooftopCourse.Rellekka,
            obstacles =
                listOf(
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_rellekka_wallclimb"),
                        name = "Rough wall",
                        xp = 20.0,
                        start = tile(2625, 3677, 0),
                        move = Climb(tile(2625, 3676, 3)),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_rellekka_gap_1"),
                        name = "Gap",
                        xp = 30.0,
                        start = tile(2622, 3672, 3),
                        move = Leap(tile(2622, 3668, 3)),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_rellekka_tightrope_1"),
                        name = "Tightrope",
                        xp = 40.0,
                        start = tile(2622, 3658, 3),
                        move =
                            Balance(
                                listOf(tile(2623, 3658, 3)) +
                                    line(tile(2623, 3658, 3), tile(2627, 3654, 3))
                            ),
                        failure = ObstacleFailure(85, tile(2624, 3656, 0), 2, 6),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_rellekka_gap_2"),
                        name = "Gap",
                        xp = 85.0,
                        start = tile(2629, 3655, 3),
                        move = Leap(tile(2639, 3653, 3), ticks = 3),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_rellekka_gap_3"),
                        name = "Gap",
                        xp = 25.0,
                        start = tile(2643, 3653, 3),
                        move = Leap(tile(2643, 3657, 3), seq = AgilityAnims.JUMP_UP),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_rellekka_tightrope_3"),
                        name = "Tightrope",
                        xp = 105.0,
                        start = tile(2647, 3662, 3),
                        move =
                            Balance(
                                listOf(tile(2647, 3663, 3)) +
                                    line(tile(2647, 3663, 3), tile(2653, 3669, 3)) +
                                    line(tile(2653, 3669, 3), tile(2655, 3669, 3))
                            ),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_rellekka_dropoff"),
                        name = "Pile of fish",
                        xp = 20.0,
                        lapBonusXp = 455.0,
                        start = tile(2655, 3676, 3),
                        move = Leap(tile(2652, 3676, 0), seq = AgilityAnims.JUMP_DOWN),
                    ),
                ),
            markTiles =
                listOf(
                    tile(2624, 3674, 3),
                    tile(2620, 3663, 3),
                    tile(2628, 3652, 3),
                    tile(2641, 3651, 3),
                    tile(2646, 3659, 3),
                    tile(2656, 3672, 3),
                ),
        )

    private fun ardougne(): CourseLayout =
        CourseLayout(
            course = RooftopCourse.Ardougne,
            obstacles =
                listOf(
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_ardy_wallclimb"),
                        name = "Wooden beams",
                        xp = 43.0,
                        start = tile(2673, 3298, 0),
                        move = Climb(tile(2671, 3299, 3)),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_ardy_jump"),
                        name = "Gap",
                        xp = 65.0,
                        start = tile(2671, 3309, 3),
                        move = Leap(tile(2665, 3318, 3), ticks = 3),
                        failure = ObstacleFailure(95, tile(2668, 3314, 0), 3, 8),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_ardy_plank"),
                        name = "Plank",
                        xp = 50.0,
                        start = tile(2662, 3318, 3),
                        move = Balance(line(tile(2662, 3318, 3), tile(2656, 3318, 3))),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_ardy_jump_2"),
                        name = "Gap",
                        xp = 21.0,
                        start = tile(2654, 3318, 3),
                        move = Leap(tile(2653, 3314, 3)),
                        failure = ObstacleFailure(95, tile(2653, 3316, 0), 3, 8),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_ardy_jump_3"),
                        name = "Gap",
                        xp = 28.0,
                        start = tile(2653, 3310, 3),
                        move = Leap(tile(2651, 3308, 3)),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_ardy_wallcrossing"),
                        name = "Steep roof",
                        xp = 57.0,
                        start = tile(2653, 3300, 3),
                        move =
                            Balance(
                                listOf(tile(2654, 3300, 3)) +
                                    line(tile(2654, 3300, 3), tile(2657, 3297, 3))
                            ),
                    ),
                    RooftopObstacle(
                        locs = listOf("loc.rooftops_ardy_jump_4"),
                        name = "Gap",
                        xp = 25.0,
                        lapBonusXp = 600.0,
                        start = tile(2657, 3297, 3),
                        move = Leap(tile(2668, 3297, 0), seq = AgilityAnims.JUMP_DOWN, ticks = 3),
                    ),
                ),
            markTiles =
                listOf(
                    tile(2671, 3305, 3),
                    tile(2664, 3318, 3),
                    tile(2653, 3312, 3),
                    tile(2651, 3306, 3),
                    tile(2653, 3302, 3),
                ),
        )
}
