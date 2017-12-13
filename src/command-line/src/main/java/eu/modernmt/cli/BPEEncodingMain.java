package eu.modernmt.cli;

import eu.modernmt.decoder.neural.bpe.SubwordTextProcessor;
import org.apache.commons.cli.*;

import java.io.File;

/**
 * Created by davide on 17/12/15.
 */
public class BPEEncodingMain {

    private static class Args {

        private static final Options cliOptions;

        static {
            Option model = Option.builder("m").longOpt("model").hasArg().required().build();
            Option isSource = Option.builder("s").longOpt("isSource").hasArg().build();
            Option text = Option.builder("t").longOpt("text").hasArg().required().build();

            cliOptions = new Options();
            cliOptions.addOption(model);
            cliOptions.addOption(isSource);
            cliOptions.addOption(text);
        }

        public final File model;
        public final boolean isSource;
        public final String text;

        public Args(String[] args) throws ParseException {
            CommandLineParser parser = new DefaultParser();
            CommandLine cli = parser.parse(cliOptions, args);

            model = new File(cli.getOptionValue('m'));
            isSource = cli.hasOption('s') && Boolean.parseBoolean(cli.getOptionValue('s'));
            text = cli.getOptionValue('t');
        }

    }

    public static void main(String[] _args) throws Throwable {
        Args args = new Args(_args);

        SubwordTextProcessor stp = SubwordTextProcessor.loadFromFile(args.model);

        String[] result = stp.encode(args.text.split(" "), args.isSource);
        for (String s : result)
            System.out.print(s + " ");
    }

}
