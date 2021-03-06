//
// Created by Davide  Caroselli on 03/12/15.
//

#ifndef JNIMOSES_MOSESDECODER_H
#define JNIMOSES_MOSESDECODER_H

#include <stdint.h>
#include <vector>
#include <utility>
#include <string>
#include <map>
#include <float.h>
#include <mmt/IncrementalModel.h>
#include <mmt/vocabulary/Vocabulary.h>

typedef struct {
    bool stateless;
    bool tunable;
    std::string name;
    void *ptr;
} feature_t;

typedef struct {
    std::string text;
    float score;
    std::string fvals;
} hypothesis_t;

typedef struct {
    std::string text;
    int64_t session;
    std::vector<hypothesis_t> hypotheses;
    std::vector<std::pair<size_t, size_t> > alignment;
} translation_t;

typedef struct {
    std::string sourceSent;
    size_t nBestListSize; //< set to 0 if no n-best list requested
} translation_request_t;

struct raw_translation_unit {
    mmt::channel_t channel;
    mmt::seqid_t position;

    mmt::memory_t memory;
    std::string source;
    std::string target;
    mmt::alignment_t alignment;
};

namespace mmt {
    namespace decoder {
        class MosesDecoder {
        public:
            static constexpr float UNTUNEABLE_COMPONENT = FLT_MAX;

            static MosesDecoder *createInstance(const std::string &inifilePath, const std::string &vocabularyPath);

            virtual std::vector<feature_t> getFeatures() = 0;

            virtual std::vector<float> getFeatureWeights(feature_t &feature) = 0;

            /**
             * Change moses feature weights to the provided featureWeights.
             *
             * Ordering guarantees:
             * * this call will not affect any translations that are in progress.
             * * this call will affect every translation request after its completion.
             *
             * This does not change the 'moses.ini' file itself.
             */
            virtual void setDefaultFeatureWeights(const std::map<std::string, std::vector<float>> &featureWeights) = 0;

            /**
             * Translate a sentence.
             *
             * @param text                source sentence with space-separated tokens
             * @param translationContext  context weights
             * @param nbestListSize       if non-zero, produce an n-best list of this size in the translation_t result
             */
            virtual translation_t translate(const std::string &text,
                                            const std::map<std::string, float> *translationContext,
                                            size_t nbestListSize) = 0;

            virtual void DeliverUpdates(const std::vector<raw_translation_unit> &translationUnits,
                                        const std::vector<mmt::deletion> &deletions,
                                        const std::unordered_map<mmt::channel_t, mmt::seqid_t> &channelPositions) = 0;

            virtual std::unordered_map<channel_t, seqid_t> GetLatestUpdatesIdentifiers() = 0;

            virtual ~MosesDecoder() {}

        };
    }
}

#endif //JNIMOSES_MOSESDECODER_H