package com.example.data.sample

import com.example.R
import com.example.data.model.Category
import com.example.data.model.Movie

/**
 * STRICTLY ISOLATED UI PREVIEW SAMPLE CATALOGUE.
 *
 * - Kept completely separate from production API/Supabase data.
 * - Used ONLY when the user explicitly taps "Explore UI Preview (Sample Catalogue)".
 * - Every movie is flagged `isSamplePreview = true` so the video player never pretends
 *   these sample items are live Telegram streams.
 */
object SampleCatalogueData {

    val categories: List<Category> = listOf(
        Category(id = "cat-scifi", name = "Sci-Fi", slug = "sci-fi", sortOrder = 1),
        Category(id = "cat-thriller", name = "Thriller", slug = "thriller", sortOrder = 2),
        Category(id = "cat-drama", name = "Drama", slug = "drama", sortOrder = 3),
        Category(id = "cat-fantasy", name = "Dark Fantasy", slug = "dark-fantasy", sortOrder = 4),
        Category(id = "cat-noir", name = "Neo-Noir", slug = "neo-noir", sortOrder = 5)
    )

    val movies: List<Movie> = listOf(
        Movie(
            id = "sample-chronos-protocol",
            title = "Chronos Protocol",
            description = "In a rain-drenched vertical megacity ruled by memory brokers, a disgraced forensic archivist uncovers an encrypted red-beam transmission that rewinds sixty seconds of reality—triggering a relentless midnight pursuit across Sector Nine.",
            categoryId = "cat-scifi",
            categoryName = "Sci-Fi",
            categories = listOf("Sci-Fi", "Neo-Noir", "Thriller"),
            releaseYear = 2026,
            durationSeconds = 7380L,
            fileSizeBytes = 186L * 1024L * 1024L,
            qualityLabel = "1080p",
            maturityRating = "16+",
            isFeatured = true,
            isTrending = true,
            isRecentlyAdded = true,
            hasTelegramThumbnail = false,
            samplePosterRes = R.drawable.img_poster_chronos_1795706650170,
            isSamplePreview = true
        ),
        Movie(
            id = "sample-solaris-eclipse",
            title = "Solaris Eclipse",
            description = "When an uncharted coronal mass ejection isolates Orbital Station Helios-4 above a ringed gas giant, mission specialist Elena Vance must execute a silent EVA crossing before the station's thermal shield fractures.",
            categoryId = "cat-drama",
            categoryName = "Drama",
            categories = listOf("Sci-Fi", "Drama"),
            releaseYear = 2025,
            durationSeconds = 8100L,
            fileSizeBytes = 194L * 1024L * 1024L,
            qualityLabel = "1080p",
            maturityRating = "13+",
            isFeatured = false,
            isTrending = true,
            isRecentlyAdded = true,
            hasTelegramThumbnail = false,
            samplePosterRes = R.drawable.img_poster_solaris_1795706650729,
            isSamplePreview = true
        ),
        Movie(
            id = "sample-velvet-dossier",
            title = "The Velvet Dossier",
            description = "Between wet Parisian cobblestones and covert embassy salons, an intelligence courier intercepts a ledger exposing four shadow syndicates—with only twelve hours until dawn to vanish without leaving a digital footprint.",
            categoryId = "cat-thriller",
            categoryName = "Thriller",
            categories = listOf("Thriller", "Neo-Noir"),
            releaseYear = 2026,
            durationSeconds = 6720L,
            fileSizeBytes = 162L * 1024L * 1024L,
            qualityLabel = "1080p",
            maturityRating = "16+",
            isFeatured = false,
            isTrending = true,
            isRecentlyAdded = false,
            hasTelegramThumbnail = false,
            samplePosterRes = R.drawable.img_poster_velvet_1795706651955,
            isSamplePreview = true
        ),
        Movie(
            id = "sample-obsidian-citadel",
            title = "Obsidian Citadel",
            description = "Across the volcanic ridge of Vaelor, the last sworn sentinel carries an ancient ember lantern toward a forsaken basalt fortress to seal a rift tearing the storm-lit heavens apart.",
            categoryId = "cat-fantasy",
            categoryName = "Dark Fantasy",
            categories = listOf("Dark Fantasy", "Drama"),
            releaseYear = 2025,
            durationSeconds = 8640L,
            fileSizeBytes = 198L * 1024L * 1024L,
            qualityLabel = "1080p",
            maturityRating = "16+",
            isFeatured = false,
            isTrending = true,
            isRecentlyAdded = true,
            hasTelegramThumbnail = false,
            samplePosterRes = R.drawable.img_poster_obsidian_1795706652201,
            isSamplePreview = true
        ),
        Movie(
            id = "sample-crimson-meridian",
            title = "Crimson Meridian",
            description = "A deep-space cartographer charts a phantom signal looping across three dead relays, revealing an abandoned vessel still broadcasting coordinates from inside the event horizon.",
            categoryId = "cat-scifi",
            categoryName = "Sci-Fi",
            categories = listOf("Sci-Fi", "Thriller"),
            releaseYear = 2024,
            durationSeconds = 6420L,
            fileSizeBytes = 148L * 1024L * 1024L,
            qualityLabel = "HD",
            maturityRating = "13+",
            isFeatured = false,
            isTrending = false,
            isRecentlyAdded = true,
            hasTelegramThumbnail = false,
            samplePosterRes = R.drawable.img_poster_solaris_1795706650729,
            isSamplePreview = true
        ),
        Movie(
            id = "sample-midnight-boulevard",
            title = "Midnight Boulevard",
            description = "A retired watchmaker in Montmartre agrees to appraise a stolen timepiece, only to realize the mechanism hides a cipher sought by two rival counter-intelligence rings.",
            categoryId = "cat-noir",
            categoryName = "Neo-Noir",
            categories = listOf("Neo-Noir", "Thriller", "Drama"),
            releaseYear = 2025,
            durationSeconds = 6180L,
            fileSizeBytes = 135L * 1024L * 1024L,
            qualityLabel = "1080p",
            maturityRating = "16+",
            isFeatured = false,
            isTrending = false,
            isRecentlyAdded = true,
            hasTelegramThumbnail = false,
            samplePosterRes = R.drawable.img_poster_velvet_1795706651955,
            isSamplePreview = true
        ),
        Movie(
            id = "sample-neon-requiem",
            title = "Neon Requiem",
            description = "When the rain never stops over New Kowloon, an acoustic engineer discovers a hidden harmonic hidden beneath citywide surveillance broadcasts.",
            categoryId = "cat-noir",
            categoryName = "Neo-Noir",
            categories = listOf("Neo-Noir", "Sci-Fi"),
            releaseYear = 2026,
            durationSeconds = 7020L,
            fileSizeBytes = 171L * 1024L * 1024L,
            qualityLabel = "1080p",
            maturityRating = "16+",
            isFeatured = false,
            isTrending = true,
            isRecentlyAdded = false,
            hasTelegramThumbnail = false,
            samplePosterRes = R.drawable.img_poster_chronos_1795706650170,
            isSamplePreview = true
        ),
        Movie(
            id = "sample-ash-and-crown",
            title = "Ash & Crown",
            description = "High above the caldera mists, two estranged royal heirs must cross the obsidian causeway before the eclipse extinguishes the citadel's eternal forge.",
            categoryId = "cat-fantasy",
            categoryName = "Dark Fantasy",
            categories = listOf("Dark Fantasy", "Thriller"),
            releaseYear = 2024,
            durationSeconds = 7680L,
            fileSizeBytes = 189L * 1024L * 1024L,
            qualityLabel = "HD",
            maturityRating = "16+",
            isFeatured = false,
            isTrending = false,
            isRecentlyAdded = false,
            hasTelegramThumbnail = false,
            samplePosterRes = R.drawable.img_poster_obsidian_1795706652201,
            isSamplePreview = true
        )
    )
}
