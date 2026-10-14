package kelompok.satu.kopiinggil

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest

// ponytail: credentials hardcoded for rapid setup; move to BuildConfig or encrypted preferences when adding multiple environments
const val SUPABASE_URL = "https://uvaeklvwkxibwcrhofku.supabase.co"
const val SUPABASE_KEY = "sb_publishable_j2vxSiilkJy3XbwCOlCgxg_k5KSJi9d"

val supabase = createSupabaseClient(
    supabaseUrl = SUPABASE_URL,
    supabaseKey = SUPABASE_KEY
) {
    install(Auth)
    install(Postgrest)
}
