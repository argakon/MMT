set(JAVA_SRC
        eu_modernmt_decoder_phrasebased_MosesDecoder.cpp
        JMosesFeature.cpp
        JMosesFeature.h
        JTranslation.cpp
        JTranslation.h)

add_library(moses_java OBJECT ${JAVA_SRC})
