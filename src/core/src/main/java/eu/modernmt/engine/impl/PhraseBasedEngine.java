package eu.modernmt.engine.impl;

import eu.modernmt.config.EngineConfig;
import eu.modernmt.config.PhraseBasedDecoderConfig;
import eu.modernmt.data.DataListener;
import eu.modernmt.decoder.Decoder;
import eu.modernmt.decoder.phrasebased.MosesDecoder;
import eu.modernmt.engine.BootstrapException;
import eu.modernmt.engine.ContributionOptions;
import eu.modernmt.engine.Engine;
import eu.modernmt.io.Paths;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.util.Collection;

/**
 * Created by davide on 22/05/17.
 */
public class PhraseBasedEngine extends Engine {

    private static final ContributionOptions CONTRIBUTION_OPTIONS = new ContributionOptions(true, true);
    private final MosesDecoder decoder;

    public PhraseBasedEngine(EngineConfig config) throws BootstrapException {
        super(config);

        try {
            PhraseBasedDecoderConfig decoderConfig = (PhraseBasedDecoderConfig) config.getDecoderConfig();

            if (decoderConfig.isEnabled())
                this.decoder = new MosesDecoder(Paths.join(this.models, "decoder"), decoderConfig.getThreads());
            else
                this.decoder = null;
        } catch (IOException e) {
            throw new BootstrapException("Failed to instantiate Moses decoder", e);
        }
    }

    @Override
    public ContributionOptions getContributionOptions() {
        return CONTRIBUTION_OPTIONS;
    }

    @Override
    public Decoder getDecoder() {
        if (decoder == null)
            throw new UnsupportedOperationException("Decoder unavailable");

        return decoder;
    }

    @Override
    public void close() {
        IOUtils.closeQuietly(decoder);
        super.close();
    }

    @Override
    public Collection<DataListener> getDataListeners() {
        Collection<DataListener> listeners = super.getDataListeners();
        listeners.add(decoder);
        return listeners;
    }
}
