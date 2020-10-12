package api.core.integration

import api.core.Sequential
import api.core.activation.Activations
import api.core.initializer.*
import api.core.layer.Dense
import api.core.layer.Flatten
import api.core.layer.Input
import api.core.layer.twodim.Conv2D
import api.core.layer.twodim.ConvPadding
import api.core.layer.twodim.MaxPool2D
import api.core.loss.Losses
import api.core.metric.Accuracy
import api.core.metric.Metrics
import api.core.optimizer.Adam
import datasets.Dataset
import datasets.handlers.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val INCORRECT_AMOUNT_OF_CLASSES_1 = 11

internal class SequentialBasicTest : IntegrationTest() {
    private val testModel = Sequential.of(
        Input(
            IMAGE_SIZE,
            IMAGE_SIZE,
            NUM_CHANNELS
        ),
        Conv2D(
            filters = 32,
            kernelSize = longArrayOf(5, 5),
            strides = longArrayOf(1, 1, 1, 1),
            activation = Activations.Relu,
            kernelInitializer = HeNormal(SEED),
            biasInitializer = Zeros(),
            padding = ConvPadding.SAME,
            name = "conv2d_1"
        ),
        MaxPool2D(
            poolSize = intArrayOf(1, 2, 2, 1),
            strides = intArrayOf(1, 2, 2, 1),
            name = "maxPool_1"
        ),
        Conv2D(
            filters = 64,
            kernelSize = longArrayOf(5, 5),
            strides = longArrayOf(1, 1, 1, 1),
            activation = Activations.Relu,
            kernelInitializer = HeNormal(SEED),
            biasInitializer = Ones(),
            padding = ConvPadding.SAME,
            name = "conv2d_2"
        ),
        MaxPool2D(
            poolSize = intArrayOf(1, 2, 2, 1),
            strides = intArrayOf(1, 2, 2, 1),
            name = "maxPool_2"
        ),
        Flatten(name = "flatten_1"), // 3136
        Dense(
            outputSize = 512,
            activation = Activations.Relu,
            kernelInitializer = HeNormal(SEED),
            biasInitializer = Constant(0.1f),
            name = "dense_1"
        ),
        Dense(
            outputSize = AMOUNT_OF_CLASSES,
            activation = Activations.Linear,
            kernelInitializer = HeNormal(SEED),
            biasInitializer = HeUniform(SEED),
            name = "dense_2"
        )
    )

    @Test
    fun mnistDatasetCreation() {
        val (train, test) = Dataset.createTrainAndTestDatasets(
            TRAIN_IMAGES_ARCHIVE,
            TRAIN_LABELS_ARCHIVE,
            TEST_IMAGES_ARCHIVE,
            TEST_LABELS_ARCHIVE,
            AMOUNT_OF_CLASSES,
            ::extractImages,
            ::extractLabels
        )

        assertEquals(train.xSize(), 60000)
        assertEquals(test.xSize(), 10000)
    }

    @Test
    fun trainingLeNetModel() {
        val (train, test) = Dataset.createTrainAndTestDatasets(
            TRAIN_IMAGES_ARCHIVE,
            TRAIN_LABELS_ARCHIVE,
            TEST_IMAGES_ARCHIVE,
            TEST_LABELS_ARCHIVE,
            AMOUNT_OF_CLASSES,
            ::extractImages,
            ::extractLabels
        )

        testModel.use {
            it.compile(optimizer = Adam(), loss = Losses.SOFT_MAX_CROSS_ENTROPY_WITH_LOGITS, metric = Accuracy())

            val trainingHistory =
                it.fit(dataset = train, epochs = EPOCHS, batchSize = TRAINING_BATCH_SIZE, verbose = false)

            assertEquals(trainingHistory.batchHistory.size, 60)
            assertEquals(1, trainingHistory.batchHistory[0].epochIndex)
            assertEquals(0, trainingHistory.batchHistory[0].batchIndex)
            assertTrue(trainingHistory.batchHistory[0].lossValue > 2.0f)
            assertTrue(trainingHistory.batchHistory[0].metricValue < 0.2f)

            val accuracy = it.evaluate(dataset = test, batchSize = TEST_BATCH_SIZE).metrics[Metrics.ACCURACY]

            if (accuracy != null) {
                assertTrue(accuracy > 0.7)
            }
        }
    }

    @Test
    fun trainingWithValidation() {
        val (train, test) = Dataset.createTrainAndTestDatasets(
            TRAIN_IMAGES_ARCHIVE,
            TRAIN_LABELS_ARCHIVE,
            TEST_IMAGES_ARCHIVE,
            TEST_LABELS_ARCHIVE,
            AMOUNT_OF_CLASSES,
            ::extractImages,
            ::extractLabels
        )
        val (newTrain, validation) = train.split(0.95)

        testModel.use {
            it.compile(optimizer = Adam(), loss = Losses.SOFT_MAX_CROSS_ENTROPY_WITH_LOGITS, metric = Accuracy())

            val trainingHistory =
                it.fit(
                    trainingDataset = newTrain,
                    validationDataset = validation,
                    epochs = EPOCHS,
                    trainBatchSize = TRAINING_BATCH_SIZE,
                    validationBatchSize = TEST_BATCH_SIZE,
                    verbose = true
                )

            assertEquals(57, trainingHistory.batchHistory.size)
            assertEquals(1, trainingHistory.batchHistory[0].epochIndex)
            assertEquals(0, trainingHistory.batchHistory[0].batchIndex)
            assertTrue(trainingHistory.batchHistory[0].lossValue > 2.0f)
            assertTrue(trainingHistory.batchHistory[0].metricValue < 0.2f)

            val accuracy = it.evaluate(dataset = test, batchSize = TEST_BATCH_SIZE).metrics[Metrics.ACCURACY]

            if (accuracy != null) {
                assertTrue(accuracy > 0.7)
            }
        }
    }

    @Test
    fun trainingFailedWithoutCompilation() {
        val (train, test) = Dataset.createTrainAndTestDatasets(
            TRAIN_IMAGES_ARCHIVE,
            TRAIN_LABELS_ARCHIVE,
            TEST_IMAGES_ARCHIVE,
            TEST_LABELS_ARCHIVE,
            AMOUNT_OF_CLASSES,
            ::extractImages,
            ::extractLabels
        )

        testModel.use {
            val exception =
                Assertions.assertThrows(IllegalStateException::class.java) {
                    it.fit(
                        dataset = train,
                        epochs = EPOCHS,
                        batchSize = TRAINING_BATCH_SIZE,
                        verbose = false
                    )
                }
            assertEquals(
                "The model is not compiled yet. Compile the model to use this method.",
                exception.message
            )
        }
    }

    @Test
    fun evaluatingFailedWithoutCompilation() {
        val (train, test) = Dataset.createTrainAndTestDatasets(
            TRAIN_IMAGES_ARCHIVE,
            TRAIN_LABELS_ARCHIVE,
            TEST_IMAGES_ARCHIVE,
            TEST_LABELS_ARCHIVE,
            AMOUNT_OF_CLASSES,
            ::extractImages,
            ::extractLabels
        )

        testModel.use {
            val exception =
                Assertions.assertThrows(IllegalStateException::class.java) {
                    it.evaluate(dataset = test, batchSize = TEST_BATCH_SIZE).metrics[Metrics.ACCURACY]
                }
            assertEquals(
                "The model is not compiled yet. Compile the model to use this method.",
                exception.message
            )
        }
    }

    @Test
    fun predictionFailedWithoutCompilation() {
        val (train, test) = Dataset.createTrainAndTestDatasets(
            TRAIN_IMAGES_ARCHIVE,
            TRAIN_LABELS_ARCHIVE,
            TEST_IMAGES_ARCHIVE,
            TEST_LABELS_ARCHIVE,
            AMOUNT_OF_CLASSES,
            ::extractImages,
            ::extractLabels
        )

        testModel.use {
            val exception =
                Assertions.assertThrows(IllegalStateException::class.java) {
                    it.predict(train.getX(0))
                }
            assertEquals(
                "The model is not compiled yet. Compile the model to use this method.",
                exception.message
            )
        }
    }

    @Test
    fun softPredictionFailedWithoutCompilation() {
        val (train, test) = Dataset.createTrainAndTestDatasets(
            TRAIN_IMAGES_ARCHIVE,
            TRAIN_LABELS_ARCHIVE,
            TEST_IMAGES_ARCHIVE,
            TEST_LABELS_ARCHIVE,
            AMOUNT_OF_CLASSES,
            ::extractImages,
            ::extractLabels
        )

        testModel.use {
            val exception =
                Assertions.assertThrows(IllegalStateException::class.java) {
                    it.predictSoftly(train.getX(0))
                }
            assertEquals(
                "The model is not compiled yet. Compile the model to use this method.",
                exception.message
            )
        }
    }

    @Test
    fun predictAllFailedWithoutCompilation() {
        val (train, test) = Dataset.createTrainAndTestDatasets(
            TRAIN_IMAGES_ARCHIVE,
            TRAIN_LABELS_ARCHIVE,
            TEST_IMAGES_ARCHIVE,
            TEST_LABELS_ARCHIVE,
            AMOUNT_OF_CLASSES,
            ::extractImages,
            ::extractLabels
        )

        testModel.use {
            val exception =
                Assertions.assertThrows(IllegalArgumentException::class.java) {
                    it.predictAll(test, 256)
                }
            assertEquals(
                "The amount of images must be a multiple of batch size.",
                exception.message
            )
        }

        testModel.use {
            val exception =
                Assertions.assertThrows(IllegalStateException::class.java) {
                    it.predictAll(test, 100)
                }
            assertEquals(
                "The model is not compiled yet. Compile the model to use this method.",
                exception.message
            )
        }
    }

    @Test
    fun predictAndGetActivationsFailedWithoutCompilation() {
        val (train, test) = Dataset.createTrainAndTestDatasets(
            TRAIN_IMAGES_ARCHIVE,
            TRAIN_LABELS_ARCHIVE,
            TEST_IMAGES_ARCHIVE,
            TEST_LABELS_ARCHIVE,
            AMOUNT_OF_CLASSES,
            ::extractImages,
            ::extractLabels
        )

        testModel.use {
            val exception =
                Assertions.assertThrows(IllegalStateException::class.java) {
                    it.predictAndGetActivations(test.getX(0))
                }
            assertEquals(
                "The model is not compiled yet. Compile the model to use this method.",
                exception.message
            )
        }
    }

    @Test
    fun incorrectAmountOfClassesInTheLastDenseLayer() {
        val testModelWithSmallAmountOfClasses = Sequential.of(
            Input(
                IMAGE_SIZE,
                IMAGE_SIZE,
                NUM_CHANNELS
            ),
            Conv2D(
                filters = 32,
                kernelSize = longArrayOf(5, 5),
                strides = longArrayOf(1, 1, 1, 1),
                activation = Activations.Relu,
                kernelInitializer = HeNormal(SEED),
                biasInitializer = Zeros(),
                padding = ConvPadding.SAME,
                name = "conv2d_1"
            ),
            MaxPool2D(
                poolSize = intArrayOf(1, 2, 2, 1),
                strides = intArrayOf(1, 2, 2, 1),
                name = "maxPool_1"
            ),
            Conv2D(
                filters = 64,
                kernelSize = longArrayOf(5, 5),
                strides = longArrayOf(1, 1, 1, 1),
                activation = Activations.Relu,
                kernelInitializer = HeNormal(SEED),
                biasInitializer = Zeros(),
                padding = ConvPadding.SAME,
                name = "conv2d_2"
            ),
            MaxPool2D(
                poolSize = intArrayOf(1, 2, 2, 1),
                strides = intArrayOf(1, 2, 2, 1),
                name = "maxPool_2"
            ),
            Flatten(name = "flatten_1"), // 3136
            Dense(
                outputSize = 512,
                activation = Activations.Relu,
                kernelInitializer = HeNormal(SEED),
                biasInitializer = Constant(0.1f),
                name = "dense_1"
            ),
            Dense(
                outputSize = INCORRECT_AMOUNT_OF_CLASSES_1,
                activation = Activations.Linear,
                kernelInitializer = HeNormal(SEED),
                biasInitializer = Constant(0.1f),
                name = "dense_2"
            )
        )


        val (train, test) = Dataset.createTrainAndTestDatasets(
            TRAIN_IMAGES_ARCHIVE,
            TRAIN_LABELS_ARCHIVE,
            TEST_IMAGES_ARCHIVE,
            TEST_LABELS_ARCHIVE,
            AMOUNT_OF_CLASSES,
            ::extractImages,
            ::extractLabels
        )

        testModelWithSmallAmountOfClasses.use {
            it.compile(optimizer = Adam(), loss = Losses.SOFT_MAX_CROSS_ENTROPY_WITH_LOGITS, metric = Accuracy())

            val exception =
                Assertions.assertThrows(IllegalStateException::class.java) {
                    it.fit(
                        dataset = train,
                        epochs = EPOCHS,
                        batchSize = TRAINING_BATCH_SIZE,
                        verbose = false
                    )
                }
            assertEquals(
                "The calculated [from the Sequential model] label batch shape [1000, 11] doesn't match actual data buffer size 10000. \n" +
                        "Please, check the input label data or correct amount of classes [amount of neurons] in last Dense layer, if you have a classification problem.\n" +
                        "Highly likely, you have different amount of classes presented in data and described in model as desired output.",
                exception.message
            )
        }
    }
}