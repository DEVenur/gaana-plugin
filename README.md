# Gaana Plugin For Lavalink

A Lavalink plugin to play songs from [Gaana](https://gaana.com) - India's largest music streaming service.

## Features

- Direct API access (no external wrapper required)
- HLS streaming with seeking support
- Songs, albums, playlists, and artist pages
- Search functionality

## Installation

Add to your Lavalink's `application.yml`:

```yaml
lavalink:
  plugins:
    - dependency: "com.github.notdeltaxd:gaana-plugin:VERSION"
      repository: "https://jitpack.io"
```

Replace `VERSION` with the latest release version (without the `v` prefix).

## Configuration

```yaml
plugins:
  gaana:
    searchLimit: 20  # Maximum search results (default: 20)
```

## Logging

To enable debug logging for the Gaana source, add this to your `application.yml`:

```yaml
logging:
  level:
    com.github.notdeltaxd.gaana: DEBUG
    com.github.notdeltaxd.plugin: DEBUG
```

## Supported URLs and Queries

### Search
- `gnasearch:arijit singh` - Search for songs

### Direct URLs
- `https://gaana.com/song/apna-bana-le-piya` - Single song
- `https://gaana.com/album/bhediya` - Album
- `https://gaana.com/playlist/gaana-dj-bollywood-top-50-1` - Playlist
- `https://gaana.com/artist/arijit-singh` - Artist top tracks

## Full Example Configuration

```yaml
server:
  port: 2333
  address: 0.0.0.0

lavalink:
  plugins:
    - dependency: "com.github.notdeltaxd:gaana-plugin:VERSION"
      repository: "https://jitpack.io"
  server:
    password: "youshallnotpass"
    sources:
      youtube: false
      bandcamp: true
      soundcloud: true
      twitch: true
      vimeo: true
      http: true
      local: false

plugins:
  gaana:
    searchLimit: 20

logging:
  level:
    root: INFO
    lavalink: INFO
    com.github.notdeltaxd: DEBUG  # Enable Gaana plugin logging

```

## Building from Source

```bash
./gradlew clean build
```

The plugin JAR will be in `plugin/build/libs/gaana-plugin-VERSION.jar`

## Contributing

Contributions are welcome! Feel free to open issues or submit pull requests.

## License

Apache License 2.0 - See [LICENSE](LICENSE) for details.
