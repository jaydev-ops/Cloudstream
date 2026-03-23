

expect object PluginStorage {
    fun save(plugins: List<PluginConfig>) // Uses RepoPlugin
    fun load(): List<PluginConfig>        // Uses RepoPlugin
}