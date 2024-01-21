package top.mcfpp.command.tree

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import top.mcfpp.Project
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.zip.GZIPInputStream

/**
 * @author SPGoding, XiLaiTL
 * @see (https://github.com/SpyglassMC/Spyglass.git) Downloader
 */

/*
MIT License

Copyright (c) 2019-2022 SPGoding

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.


 */

fun parseGzippedJson(buffer: ByteArray): Any? {
    GZIPInputStream(buffer.inputStream()).use {
        return JSON.parse(it.readAllBytes())
    }
}

enum class DownloadResourceType{
    blocks,commands,registries
}

class DownloadHelper {
    companion object{
        private fun getUri(
            source:String,
            user:String,
            repo:String,
            tag:String,
            path:String
            ): String = when(source){
            "fastly"-> "https://fastly.jsdelivr.net/gh/${user}/${repo}@${tag}/${path}"
            "github"-> "https://raw.githubusercontent.com/${user}/${repo}/${tag}/${path}"
            "jsdelivr"-> "https://cdn.jsdelivr.net/gh/${user}/${repo}@${tag}/${path}"
            else -> ""
        }

        private fun getMcmetaSummaryUri(
            type:DownloadResourceType,
            version: String,
            isLatest: Boolean,
            source: String,
        ):String{
            val tag = if(isLatest) "summary" else "${version}-summary"
            val path = "${type}/data.json.gz"
            val res = getUri(
                source.lowercase(Locale.getDefault()),
                user = "misode", repo = "mcmeta",tag, path)
            return res
        }
        private fun getMcmetaSummary(
            type: DownloadResourceType,
            version: String,
            isLatest: Boolean,
            source: String,
        ):Any? // JSON
        {
            val ref = Downloader.getGitRef(
                defaultBranch = "summary",
                getTag = { "${it}-summary"},
                isLatest,
                version
            )
            val uri = getMcmetaSummaryUri(type,version,isLatest,source)
            var checksum:String? = null
            fun getResource(type:DownloadResourceType):Any?{
                val out = Downloader.DownloadOut(null, null)
                val data = Downloader.download(
                    Downloader.DownloadJob(
                        id = "mc-je/${version}/${type}.json.gz",
                        uri,
                        transformer = {
                            parseGzippedJson(it)
                        },
                        cache = Downloader.DownloadCache.getCacheOptionsBasedOnGitHubCommitSha("misode", "mcmeta", ref),
                        ttl = Downloader.DownloaderTtl,
                        options = null
                    ), out
                )
                checksum = if(checksum==null ) out.checksum else checksum
                return data
            }
            return getResource(DownloadResourceType.commands)
        }
        fun getMcmetaCommands(
            version: String,
            isLatest: Boolean,
            source: String,
        ):CommandTreeNode{
            return JSON.to(CommandTreeNode::class.java, getMcmetaSummary(DownloadResourceType.commands,version,isLatest,source))
        }
    }
}


class Downloader(){
    class ExternalDownloaderOptions(
        val headers: MutableMap<String,String>?,
        val timeout: Int?
    ){}


    class DownloadJob<R>(
        val id: String?,
        val uri: String,//"http://" "https://"
        val cache: DownloadCache?,
        val transformer:(data: ByteArray)->R,
        val options: ExternalDownloaderOptions?,
        val ttl: Int?
    ){
        constructor(job: DownloadJob<R>,id:String) : this(
            id,job.uri,job.cache,job.transformer,job.options,job.ttl
        ) {}
    }

    class MemoryCache(
        var buffer: ByteArray,
        var time:Long
    ){}
    class DownloadCache(
        val checksumJob: DownloadJob<String>,
        val checksumExtension: String, //".${extension}"
        val serializer: ((data: ByteArray)->ByteArray)?,
        val deserializer: ((data: ByteArray)->ByteArray)?
    ){
        class GitHubRefResponse(
            var message: String?,
            var ref: String,
            var `object`:ShaObject
        ){
            class ShaObject(var sha:String)
        }
        companion object{
            fun getCacheOptionsBasedOnGitHubCommitSha(
                owner: String,
                repo: String,
                ref: String,
            ):DownloadCache {
                return DownloadCache(
                    checksumExtension = ".commit-sha",
                    checksumJob = DownloadJob(
                        uri = "https://api.github.com/repos/${owner}/${repo}/git/${ref}",
                        transformer = Downloader::getGitHubRefResponseSha,
                        options = GitHubApiDownloadOptions,
                        ttl = DownloaderTtl,
                        id = null,
                        cache = null
                    ),
                    deserializer = null,
                    serializer = null
                )
            }
        }
    }

    class DownloadOut(
        var cacheUri: Path?,
        var checksum: String?
    ) {}
    companion object{
        val DownloaderTtl = 15_000
        val GitHubApiDownloadOptions = ExternalDownloaderOptions(
            headers = mutableMapOf(
                "Accept" to "application/vnd.github.v3+json",
                "User-Agent" to "XiLaiTL"
            ),
            null
        )
        fun getGitHubRefResponseSha(buffer:ByteArray):String{
            val response = JSON.parse(buffer)
            if(response is JSONArray){
                val gitHubRefResponse = JSON.to(DownloadCache.GitHubRefResponse::class.java,response[0])
                return gitHubRefResponse.`object`.sha;
            }
            val gitHubRefResponse = JSON.to(DownloadCache.GitHubRefResponse::class.java,response)
            if(gitHubRefResponse.message==null){
                return gitHubRefResponse.`object`.sha;
            }
            else{
                throw Error(gitHubRefResponse.message)
            }
        }
        fun getGitRef(
            defaultBranch: String,
            getTag: (version: String) -> String,
            isLatest: Boolean,
            version: String
        ):String{
            return if(isLatest) "refs/heads/${defaultBranch}"
            else "refs/tags/${getTag(version)}"
        }

        val memoryCaches:MutableMap<String,MemoryCache> = mutableMapOf()
        fun <R> download(
            job: DownloadJob<R>,
            out: DownloadOut = DownloadOut(null,null)
        ): R? {
            if(job.ttl!=null&& memoryCaches.contains(job.uri)){
                val memoryCache = memoryCaches[job.uri]!!
                if(memoryCache.time <= System.currentTimeMillis()+ job.ttl){
                    Project.info(
                        "[Downloader] [${job.id}] Skipped thanks to valid cache in memory",
                    )
                    return job.transformer(memoryCache.buffer)
                }
                else{
                    memoryCaches.remove(job.uri)
                }

            }
            var checksum:String? = null
            var cacheUri:Path? = null
            var cacheChecksumUri: Path? = null
            if(job.cache!=null){
                var checksumJob = job.cache.checksumJob
                var checksumExtension = job.cache.checksumExtension
                cacheUri = Project.root.resolve("downloader/${job.id}")
                out.cacheUri = cacheUri
                cacheChecksumUri = Project.root.resolve("downloader/${job.id}${checksumExtension}")
                try{
                    if(Files.notExists(cacheUri.parent)){
                        Files.createDirectories(cacheUri.parent)
                    }
                    if(Files.notExists(cacheChecksumUri.parent)){
                        Files.createDirectories(cacheChecksumUri.parent)
                    }
                }
                catch (_:IOException){ }

                try{
                    checksum = download(DownloadJob(checksumJob,job.id+checksumExtension))
                    out.checksum = checksum
                    try{
                        val cacheChecksum = Files.readString(cacheChecksumUri)
                        if(checksum == cacheChecksum){
                            try {
                                if(cacheUri!=null){
                                    val cachedBuffer = Files.readAllBytes(cacheUri)
                                    if(job.ttl!=null){
                                        memoryCaches[job.uri] = MemoryCache(cachedBuffer,System.currentTimeMillis())
                                        val deserializer = job.cache.deserializer?:{it}
                                        Project.info("[Downloader] [${job.id}] Skipped downloading thanks to cache ${cacheChecksum}",)
                                        return job.transformer(deserializer(cachedBuffer))
                                    }
                                }
                            }
                            catch (_:FileNotFoundException){
                                try{
                                    if(cacheChecksumUri!=null)
                                        Files.delete(cacheChecksumUri)
                                }
                                catch (_:IOException){
                                    Project.error("[Downloader] [${job.id}] Removing invalid cache checksum “${cacheChecksumUri}")
                                }
                            }
                            catch (_:IOException){
                                Project.error("[Downloader] [${job.id}] Loading cached file “${cacheUri}")
                            }
                        }
                    }
                    catch (_:FileNotFoundException){ }
                    catch (_:IOException){
                        Project.error("[Downloader] [${job.id}] Loading cache checksum “${cacheChecksumUri}”",)
                    }
                }
                catch (_:IOException){
                    Project.error("[Downloader] [${job.id}] Fetching latest checksum “${checksumJob.uri}”",)
                }
            }

            try{
                URL(job.uri).openStream().use {input->
                    val buffer = input.readAllBytes()
                    if(job.ttl!=null){
                        memoryCaches[job.uri] = MemoryCache(buffer,System.currentTimeMillis())
                    }
                    if(job.cache!=null&&cacheUri!=null&&cacheChecksumUri!=null){
                        if(checksum!=null){
                            try{
                                Files.writeString(cacheChecksumUri,checksum)
                            }
                            catch (_:IOException){
                                Project.error("[Downloader] [${job.id}] Saving cache checksum “${cacheChecksumUri}")
                            }
                        }
                        try{
                            val serializer = job.cache.serializer?:{it}
                            Files.write(cacheUri,serializer(buffer))
                        }
                        catch (_:IOException){
                            Project.error("[Downloader] [${job.id}] Caching file “${cacheUri}")
                        }
                    }
                    Project.info("[Downloader] [${job.id}] Downloaded from “${job.uri}”")
                    return job.transformer(buffer)
                }

            }
            catch (_:IOException){
                Project.error("[Downloader] [${job.id}] Downloading “${job.uri}”")
            }
            return null
        }

    }

}


