package timeseriesweka.classifiers.distance_based.ee;

import evaluation.evaluators.BespokeTrainEstimateEvaluator;
import evaluation.evaluators.Evaluator;
import evaluation.storage.ClassifierResults;
import evaluation.tuning.ParameterSpace;
import timeseriesweka.classifiers.Seedable;
import timeseriesweka.classifiers.TrainAccuracyEstimator;
import timeseriesweka.classifiers.distance_based.distances.DistanceMeasure;
import timeseriesweka.classifiers.distance_based.distances.ddtw.Ddtw;
import timeseriesweka.classifiers.distance_based.distances.ddtw.DdtwParameterSpaceBuilder;
import timeseriesweka.classifiers.distance_based.distances.ddtw.FullDdtwParameterSpaceBuilder;
import timeseriesweka.classifiers.distance_based.distances.dtw.DtwParameterSpaceBuilder;
import timeseriesweka.classifiers.distance_based.distances.dtw.EdParameterSpaceBuilder;
import timeseriesweka.classifiers.distance_based.distances.dtw.FullDtwParameterSpaceBuilder;
import timeseriesweka.classifiers.distance_based.distances.erp.ErpParameterSpaceBuilder;
import timeseriesweka.classifiers.distance_based.distances.lcss.LcssParameterSpaceBuilder;
import timeseriesweka.classifiers.distance_based.distances.msm.MsmParameterSpaceBuilder;
import timeseriesweka.classifiers.distance_based.distances.twed.TwedParameterSpaceBuilder;
import timeseriesweka.classifiers.distance_based.distances.wddtw.Wddtw;
import timeseriesweka.classifiers.distance_based.distances.wddtw.WddtwParameterSpaceBuilder;
import timeseriesweka.classifiers.distance_based.distances.wdtw.WdtwParameterSpaceBuilder;
import timeseriesweka.classifiers.distance_based.ee.selection.KBestSelector;
import timeseriesweka.classifiers.distance_based.knn.Knn;
import timeseriesweka.filters.cache.CachedFunction;
import utilities.ArrayUtilities;
import utilities.StringUtilities;
import utilities.iteration.AbstractIterator;
import utilities.iteration.ClassifierIterator;
import utilities.iteration.ParameterSetIterator;
import utilities.iteration.random.RandomIterator;
import weka.classifiers.AbstractClassifier;
import weka.core.Instance;
import weka.core.Instances;

import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import static experiments.data.DatasetLoading.sampleDataset;
import static utilities.GenericTools.indexOfMax;

public class Ee
    extends AbstractClassifier
    implements TrainAccuracyEstimator {

    private final Random trainRandom = new Random();
    private final Random testRandom = new Random();

    private CachedFunction<Instance, Integer, Instance> derivativeCache;

    private List<Function<Instances, ParameterSpace>> parameterSpaceFunctions = new ArrayList<>(Arrays.asList(
//        i -> new EdParameterSpaceBuilder().build()//,
        i -> new DtwParameterSpaceBuilder().build(i)//,
//        i -> new FullDtwParameterSpaceBuilder().build(),
//        i -> new DdtwParameterSpaceBuilder().build(i),
//        i -> new FullDdtwParameterSpaceBuilder().build(),
//        i -> new WdtwParameterSpaceBuilder().build(),
//        i -> new WddtwParameterSpaceBuilder().build(),
//        i -> new LcssParameterSpaceBuilder().build(i),
//        i -> new MsmParameterSpaceBuilder().build(),
//        i -> new ErpParameterSpaceBuilder().build(i),
//        i -> new TwedParameterSpaceBuilder().build()
                                                                                                             ));
    private Long trainSeed;
    private Long testSeed;
    private AbstractIterator<Member> memberIterator;
    private List<Benchmark> constituents;
    private List<Member> members;
    private boolean estimateTrain = true;
    private String trainResultsPath;
    private ClassifierResults trainResults;
    private int trainInstancesSize;
    private int minTrainSize = -1;
    private final Logger logger = Logger.getLogger(Ee.class.getCanonicalName());

    public Logger getLogger() {
        return logger;
    }

    public class Member {

        private final AbstractIterator<AbstractClassifier> source;
        private final AbstractIterator<AbstractClassifier> improvement;

        public AbstractIterator<AbstractClassifier> getImprovement() {
            return improvement;
        }

        private final AbstractIterator<AbstractClassifier> iterator;
        private final KBestSelector<Benchmark, Double> selector;
        private final Evaluator evaluator;

        public Member(final AbstractIterator<AbstractClassifier> source,
                      final AbstractIterator<AbstractClassifier> improvement,
                      final KBestSelector<Benchmark, Double> selector) {
            this.source = source;
            this.improvement = improvement;
            this.iterator = new AbstractIterator<AbstractClassifier>() {

                private AbstractIterator<AbstractClassifier> previous;

                @Override
                public void add(final AbstractClassifier item) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public AbstractIterator<AbstractClassifier> iterator() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean hasNext() {
                    return source.hasNext() || improvement.hasNext();
                }

                @Override
                public AbstractClassifier next() {
                    boolean anotherSource = source.hasNext();
                    boolean anotherImprovement = improvement.hasNext();
                    boolean choice = anotherSource;
                    AbstractClassifier classifier;
                    if (anotherSource && anotherImprovement) {
                        choice = trainRandom.nextBoolean();
                    }
                    if (choice) {
                        previous = source;
                        classifier = nextSource();
                    } else {
                        previous = improvement;
                        classifier = nextImprovement();
                    }
                    return classifier;
                }

                @Override
                public void remove() {
                    previous.remove();
                }
            };
            this.selector = selector;
            this.evaluator = new BespokeTrainEstimateEvaluator();
        }

        private AbstractClassifier nextSource() {
            AbstractClassifier classifier = source.next();
            if(classifier instanceof Knn) {
                Knn knn = (Knn) classifier;
                DistanceMeasure distanceMeasure = knn.getDistanceMeasure();
                distanceMeasure.setCacheDistances(true);
                if(distanceMeasure instanceof Ddtw || distanceMeasure instanceof Wddtw) {
                    if(derivativeCache == null) {
                        derivativeCache = new Ddtw().getDerivativeCache();
                    }
                    if(distanceMeasure instanceof Ddtw) {
                        ((Ddtw) distanceMeasure).setDerivativeCache(derivativeCache);
                    }
                    if(distanceMeasure instanceof Wddtw) {
                        ((Wddtw) distanceMeasure).setDerivativeCache(derivativeCache);
                    }
                }
            }
            if(classifier instanceof Seedable) {
                ((Seedable) classifier).setTrainSeed(trainSeed);
                ((Seedable) classifier).setTestSeed(testSeed);
            }
            return classifier;
        }

        private AbstractClassifier nextImprovement() {
            AbstractClassifier classifier = improvement.next();
            classifier = improve(classifier);
            return classifier;
        }

        public AbstractIterator<AbstractClassifier> getIterator() {
            return iterator;
        }

        public KBestSelector<Benchmark, Double> getSelector() {
            return selector;
        }

        public Evaluator getEvaluator() {
            return evaluator;
        }
    }

    public static void main(String[] args) throws
                                           Exception {
        long seed = 0;
        Instances[] dataset = sampleDataset("/home/vte14wgu/Projects/datasets/Univariate2018/", "GunPoint", (int) seed);
        Instances train = dataset[0];
        Instances test = dataset[1];
        Ee ee = new Ee();
//        ee.getLogger().setLevel(Level.OFF);
        ee.setTrainSeed(seed);
        ee.setTestSeed(seed);
        ee.setFindTrainAccuracyEstimate(true);
        ee.buildClassifier(train);
        ClassifierResults trainResults = ee.getTrainResults();
        System.out.println("train acc: " + trainResults.getAcc());
        System.out.println("-----");
        ClassifierResults testResults = new ClassifierResults();
        for (Instance testInstance : test) {
            long time = System.nanoTime();
            double[] distribution = ee.distributionForInstance(testInstance);
            double prediction = indexOfMax(distribution);
            time = System.nanoTime() - time;
            testResults.addPrediction(testInstance.classValue(), distribution, prediction, time, null);
        }
        System.out.println(testResults.getAcc());
    }

    @Override
    public void buildClassifier(Instances trainInstances) throws
                                                          Exception {
        setup(trainInstances);
        while (memberIterator.hasNext()) {
            Member member = memberIterator.next();
            AbstractIterator<AbstractClassifier> iterator = member.getIterator();
            AbstractClassifier classifier = iterator.next();
            iterator.remove();
            ClassifierResults trainResults = member.getEvaluator().evaluate(classifier, trainInstances);
            Benchmark benchmark = new Benchmark(classifier, trainResults);
            logger.info(trainResults.getAcc() + " for " + classifier.toString() + " " + StringUtilities.join(", ", classifier.getOptions()));
            member.getSelector().add(benchmark);
            feedback(member, benchmark);
            if(!iterator.hasNext()) {
                memberIterator.remove();
            }
        }
        constituents = new ArrayList<>();
        for(Member member : members) {
            List<Benchmark> selected = member.getSelector().getSelectedAsList();
            Benchmark choice = ArrayUtilities.randomChoice(selected, trainRandom);
            System.out.println(StringUtilities.join(", " , choice.getClassifier().getOptions()));
            constituents.add(choice);
        }
        if(estimateTrain) {
            trainResults = new ClassifierResults();
            for(int i = 0; i < trainInstances.size(); i++) {
                long time = System.nanoTime();
                double[] distribution = new double[trainInstances.numClasses()];
                for(Benchmark constituent : constituents) {
                    ClassifierResults constituentTrainResults = constituent.getResults();
                    double[] constituentDistribution = constituentTrainResults.getProbabilityDistribution(i);
                    ArrayUtilities.multiplyInPlace(constituentDistribution, constituentTrainResults.getAcc());
                    ArrayUtilities.addInPlace(distribution, constituentDistribution);
                }
                ArrayUtilities.normaliseInPlace(distribution);;
                int prediction = ArrayUtilities.bestIndex(Arrays.asList(ArrayUtilities.box(distribution)), trainRandom);
                time = System.nanoTime() - time;
                Instance trainInstance = trainInstances.get(i);
                trainResults.addPrediction(trainInstance.classValue(),
                                           distribution,
                                           prediction,
                                           time,
                                           null);
            }
            if(trainResultsPath != null) {
                trainResults.writeFullResultsToFile(trainResultsPath);
            }
        }
    }

    private void setup(Instances trainInstances) {
        if(trainSeed == null) {
            logger.warning("train seed not set");
        }
        if(testSeed == null) {
            logger.warning("test seed not set");
        }
        if(trainInstances.isEmpty()) {
            throw new IllegalArgumentException("train instances empty");
        }
        if(parameterSpaceFunctions.isEmpty()) {
            throw new IllegalStateException("no constituents given");
        }
        derivativeCache = null;
        trainInstancesSize = trainInstances.size();
        members = new ArrayList<>();
        memberIterator = new RandomIterator<>(trainRandom);
        for (Function<Instances, ParameterSpace> function : parameterSpaceFunctions) {
            ParameterSpace parameterSpace = function.apply(trainInstances);
            parameterSpace.removeDuplicateParameterSets();
            if (parameterSpace.size() > 0) {
                AbstractIterator<Integer> iterator = new RandomIterator<>(trainRandom);
                ParameterSetIterator parameterSetIterator = new ParameterSetIterator(parameterSpace, iterator);
                ClassifierIterator classifierIterator = new ClassifierIterator();
                classifierIterator.setParameterSetIterator(parameterSetIterator);
                classifierIterator.setSupplier(() -> {
                    Knn knn = new Knn();
                    knn.setTrainSize(minTrainSize);

                    return knn;
                });
                KBestSelector<Benchmark, Double> selector = new KBestSelector<>(Double::compare);
                selector.setLimit(1);
                selector.setExtractor(benchmark -> benchmark.getResults().getAcc());
                Member member = new Member(classifierIterator, new RandomIterator<>(trainRandom), selector);
                memberIterator.add(member);
                members.add(member);
            }
        }
    }

    private void feedback(Member member, Benchmark benchmark) {
        AbstractClassifier classifier = benchmark.getClassifier();
        if (canImprove(classifier)) {
            member.getImprovement().add(classifier);
        }
    }

    private boolean canImprove(AbstractClassifier classifier) {
        if (classifier instanceof Knn) {
            Knn knn = (Knn) classifier;
            int trainSize = knn.getTrainSize();
            return trainSize + 1 <= trainInstancesSize && trainSize >= 0;
        }
        throw new UnsupportedOperationException();
    }

    private AbstractClassifier improve(AbstractClassifier classifier) {
        if (classifier instanceof Knn) {
            Knn knn = (Knn) classifier;
            try {
                knn = knn.shallowCopy();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            int trainSize = knn.getTrainSize();
            knn.setTrainSize(trainSize + 1);
            return knn;
        }
        throw new UnsupportedOperationException();
    }

    public List<Function<Instances, ParameterSpace>> getParameterSpaceFunctions() {
        return parameterSpaceFunctions;
    }

    public void setParameterSpaceFunctions(List<Function<Instances, ParameterSpace>> parameterSpaceFunctions) {
        this.parameterSpaceFunctions = parameterSpaceFunctions;
    }

    @Override
    public double classifyInstance(Instance testInstance) throws
                                                          Exception {
        double[] distribution = distributionForInstance(testInstance);
        return ArrayUtilities.bestIndex(Arrays.asList(ArrayUtilities.box(distribution)), testRandom);
    }

    @Override
    public double[] distributionForInstance(Instance testInstance) throws
                                                                   Exception {
        double[] distribution = new double[testInstance.numClasses()];
        for (Benchmark constituent : constituents) {
            double weight = constituent.getResults()
                                       .getAcc();
            double[] constituentDistribution = constituent.getClassifier()
                                                          .distributionForInstance(testInstance);
            ArrayUtilities.multiplyInPlace(constituentDistribution, weight);
            ArrayUtilities.addInPlace(distribution, constituentDistribution);
        }
        ArrayUtilities.normaliseInPlace(distribution);
        return distribution;
    }

    public Long getTrainSeed() {
        return trainSeed;
    }

    public void setTrainSeed(final Long trainSeed) {
        this.trainSeed = trainSeed;
    }

    public Long getTestSeed() {
        return testSeed;
    }

    public void setTestSeed(final Long testSeed) {
        this.testSeed = testSeed;
    }

    @Override
    public void setFindTrainAccuracyEstimate(final boolean estimateTrain) {
        this.estimateTrain = estimateTrain;
    }

    @Override
    public void writeTrainEstimatesToFile(final String path) {
        trainResultsPath = path;
    }

    @Override
    public ClassifierResults getTrainResults() {
        return trainResults;
    }

    @Override
    public String getParameters() {
        return StringUtilities.join(",", getOptions());
    }
}
