package com.uploadpoc.core.cartology.parser;

import com.uploadpoc.core.cartology.cache.CartologyConfigurationCache;
import com.uploadpoc.core.cartology.model.ParsedFilename;
import com.uploadpoc.core.cartology.normalizer.CartologyNameNormalizer;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Mapping-aware filename parser for Cartology DAM assets.
 * <p>
 * Supports two filename patterns:
 * <ol>
 *   <li><b>With campaign type:</b>
 *       {@code Channel_Campaign-Type_Media-Format_Asset-Name.ext}</li>
 *   <li><b>Without campaign type:</b>
 *       {@code Channel_Media-Format_Asset-Name.ext}</li>
 * </ol>
 * <p>
 * The parser uses the {@link CartologyConfigurationCache} to identify which
 * underscore-delimited segments correspond to the channel, campaign type, and
 * media format — rather than relying on naive {@code String.split("_")}
 * positional indexing.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Extract the file extension (everything after the last {@code .}).</li>
 *   <li>Split the basename by {@code _} into segments.</li>
 *   <li>Try to match the <em>channel</em> from the left (1 segment, then
 *       2 segments, etc.) against known channels in the cache.</li>
 *   <li>Determine whether the matched channel uses campaign types.</li>
 *   <li>If yes, the next segment is the campaign type.</li>
 *   <li>The next segment is the media format.</li>
 *   <li>The remaining segments (concatenated with {@code _}) are the asset
 *       name.</li>
 * </ol>
 */
@Component(service = CartologyFilenameParser.class, immediate = true)
public class CartologyFilenameParser {

    private static final Logger LOG = LoggerFactory.getLogger(CartologyFilenameParser.class);

    @Reference
    private CartologyConfigurationCache configurationCache;

    @Reference
    private CartologyNameNormalizer nameNormalizer;

    /**
     * Parses the given filename into its segments.
     *
     * @param filename the asset filename including extension,
     *                 e.g. {@code "POS_Special_A3-Bin-Card_Template.psd"}
     * @return the parsed result, or {@code null} if the filename cannot be parsed
     */
    public ParsedFilename parse(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            LOG.debug("Filename is null or empty");
            return null;
        }

        // 1. Extract extension
        int lastDot = filename.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == filename.length() - 1) {
            LOG.debug("No valid extension found in filename: {}", filename);
            return null;
        }
        String extension = filename.substring(lastDot + 1);
        String basename = filename.substring(0, lastDot);

        // 2. Split by underscore
        String[] segments = basename.split("_");
        if (segments.length < 3) {
            // Minimum: Channel_MediaFormat_AssetName
            LOG.debug("Too few segments in filename: {}", filename);
            return null;
        }

        Set<String> knownChannels = configurationCache.getAllChannels();

        // 3. Try to match channel from left (greedy: try multi-segment first)
        int channelEndIndex = -1;
        String matchedChannel = null;

        // Try progressively longer channel matches
        // Most channels are 1 segment, but support multi-segment (e.g. Off-Network is
        // already 1 segment after normalisation; but "Some Long Channel" would be
        // 1 segment "Some-Long-Channel" after normalization too, since spaces become
        // hyphens and underscores are the delimiter).
        // In practice, each segment between underscores is already a normalised token.
        for (int i = 1; i <= segments.length - 2; i++) {
            StringBuilder channelBuilder = new StringBuilder();
            for (int j = 0; j < i; j++) {
                if (j > 0) channelBuilder.append("_");
                channelBuilder.append(segments[j]);
            }
            String candidate = channelBuilder.toString();
            // The candidate is already in normalised form (hyphens, no spaces) in the filename
            if (knownChannels.contains(candidate)) {
                matchedChannel = candidate;
                channelEndIndex = i;
                // Don't break — try longer matches (prefer longest match)
            }
        }

        if (matchedChannel == null) {
            // Fallback: treat first segment as channel even if unknown
            // (validation will catch UNKNOWN_CHANNEL later)
            matchedChannel = segments[0];
            channelEndIndex = 1;
            LOG.debug("Channel '{}' not found in cache, using first segment", matchedChannel);
        }

        // 4. Determine if channel has campaign types
        boolean hasCampaignType = configurationCache.channelHasCampaignType(matchedChannel);

        int remaining = segments.length - channelEndIndex;

        if (hasCampaignType) {
            // Pattern: Channel_CampaignType_MediaFormat_AssetName
            if (remaining < 3) {
                LOG.debug("Channel '{}' expects campaign type but not enough segments: {}",
                        matchedChannel, filename);
                return null;
            }
            String campaignType = segments[channelEndIndex];
            String mediaFormat = segments[channelEndIndex + 1];
            String assetName = joinSegments(segments, channelEndIndex + 2);

            return new ParsedFilename(matchedChannel, campaignType, mediaFormat,
                    assetName, extension);
        } else {
            // Pattern: Channel_MediaFormat_AssetName
            if (remaining < 2) {
                LOG.debug("Not enough segments after channel '{}': {}", matchedChannel, filename);
                return null;
            }
            String mediaFormat = segments[channelEndIndex];
            String assetName = joinSegments(segments, channelEndIndex + 1);

            return new ParsedFilename(matchedChannel, null, mediaFormat,
                    assetName, extension);
        }
    }

    /**
     * Joins segments from startIndex to end with underscore.
     */
    private String joinSegments(String[] segments, int startIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < segments.length; i++) {
            if (i > startIndex) sb.append("_");
            sb.append(segments[i]);
        }
        return sb.toString();
    }
}
