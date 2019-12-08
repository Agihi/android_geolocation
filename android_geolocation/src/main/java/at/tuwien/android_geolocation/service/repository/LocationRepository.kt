package at.tuwien.android_geolocation.service.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import at.tuwien.android_geolocation.service.mls.MLSRequest
import at.tuwien.android_geolocation.service.mls.MLSResponse
import at.tuwien.android_geolocation.service.model.Location
import at.tuwien.android_geolocation.service.model.LocationDao
import at.tuwien.android_geolocation.service.model.MLSAPI
import at.tuwien.android_geolocation.service.model.Position
import at.tuwien.android_geolocation.util.Result
import com.tuwien.geolocation_android.BuildConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.joda.time.DateTime
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

class LocationRepository(
    private val locationDao: LocationDao,
    private val mlsAPI: MLSAPI,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun getLocations(): Result<List<Location>> = withContext(ioDispatcher) {
        return@withContext try {
            Result.Success(locationDao.getLocations())
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getLocation(locationId: Long): Result<Location> = withContext(ioDispatcher) {
        try {
            val task = locationDao.getLocationById(locationId)
            if (task != null) {
                return@withContext Result.Success(task)
            } else {
                return@withContext Result.Error(Exception("Task not found!"))
            }
        } catch (e: Exception) {
            return@withContext Result.Error(e)
        }
    }

    suspend fun newLocation(mlsRequest: MLSRequest, gps: Position?): Result<Long> =
        withContext(ioDispatcher) {

            val mlsPosition = getMLSPosition(mlsRequest) ?: return@withContext Result.Error(
                Exception("MLS query failed")
            )

            val location = Location(
                0L,
                mls = mlsPosition,
                gps = gps ?: return@withContext Result.Error(Exception("GPS failed")),
                captureTime = DateTime.now(),
                params = mlsRequest
            )

            val locationId = locationDao.insertLocation(location)
            Log.println(Log.INFO, "location_repository", "got locationID: $locationId")
            return@withContext Result.Success(locationId)
        }

    suspend fun deleteAllLocations() = withContext(ioDispatcher) {
        locationDao.deleteLocations()
    }

    suspend fun deleteLocation(locationId: Long) = withContext<Unit>(ioDispatcher) {
        locationDao.deleteLocationById(locationId)
    }

    fun createTemporaryLocationPlaintextFile(context: Context, text: String): Uri {
        val file = File(context.cacheDir, "location.txt")

        val fos = FileOutputStream(file)
        val osw = OutputStreamWriter(fos)
        osw.write(text)
        osw.close()
        fos.close()

        return FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", file)
    }

    private fun getMLSPosition(mlsRequest: MLSRequest): Position? {
        val mlsResponse: MLSResponse?

        val response: Response<MLSResponse?>? = mlsAPI.getMLSLocation(mlsRequest)?.execute()

        return if (response != null && response.isSuccessful) {
            mlsResponse = response.body()
            val mlsResponseCopy = mlsResponse!!.copy()
            validateAndConvertMLSResponse(mlsResponseCopy)
        } else {
            null
        }
    }

    private fun validateAndConvertMLSResponse(mlsResponse: MLSResponse): Position {
        if (mlsResponse.error != null) {
            Log.println(
                Log.ERROR,
                "Error in MLS response",
                "MLS returned an error " + mlsResponse.error.code
            )
            Log.println(Log.ERROR, "Error in MLS response", mlsResponse.error.message!!)
        }

        return Position(
            longitude = mlsResponse.location?.lng!!,
            latitude = mlsResponse.location.lat!!,
            accuracy = mlsResponse.accuracy!!
        )
    }
}