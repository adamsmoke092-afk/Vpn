import re

with open('app/src/main/java/com/unitytunnel/app/MainActivity.kt', 'r') as f:
    content = f.read()

# Remove RewardedAdService import
content = re.sub(r'import com\.unitytunnel\.app\.ads\.RewardedAdService\n?', '', content)

# Remove rewardedAdService instantiation
content = re.sub(r'    val rewardedAdService = remember \{ RewardedAdService\(activity, viewModel\) \}\n?', '', content)

# Remove rewardedAdService parameter from DashboardScreen
content = re.sub(r'                        rewardedAdService = rewardedAdService,\n', '', content)

# Replace double up call
double_up_old = """                                    rewardedAdService.loadAd(onLoaded = {
                                        rewardedAdService.showAdForDoubleUp(onClosed = {
                                            viewModel.dismissDoubleUpOffer()
                                        }, onFailure = {
                                            Toast.makeText(context, "Failed to load bonus ad", Toast.LENGTH_SHORT).show()
                                        })
                                    }, onFailure = {
                                        Toast.makeText(context, "No bonus ad available", Toast.LENGTH_SHORT).show()
                                    })"""
double_up_new = """                                    viewModel.showRewardedAd(activity, "DOUBLE_UP") { success ->
                                        if (success) {
                                            viewModel.dismissDoubleUpOffer()
                                        } else {
                                            Toast.makeText(context, "Ad not ready or failed to load. Try again shortly.", Toast.LENGTH_SHORT).show()
                                        }
                                    }"""
content = content.replace(double_up_old, double_up_new)

# Remove rewardedAdService from TopUpSection signature
content = re.sub(r'    rewardedAdService: RewardedAdService,\n', '', content)

# Replace top up call
top_up_old = """                // Preload ad
                rewardedAdService.loadAd(onLoaded = {
                    rewardedAdService.showAdForTopUp(onClosed = {}, onFailure = {
                        Toast.makeText(context, "Failed to present sponsored video. Try again shortly.", Toast.LENGTH_SHORT).show()
                    })
                }, onFailure = { err ->
                    Toast.makeText(context, "No available sponsored video fill: $err", Toast.LENGTH_SHORT).show()
                })"""
top_up_new = """                viewModel.showRewardedAd(activity, "TOP_UP") { success ->
                    if (!success) {
                        Toast.makeText(context, "Sponsored video not ready. Try again shortly.", Toast.LENGTH_SHORT).show()
                    }
                }"""
content = content.replace(top_up_old, top_up_new)

with open('app/src/main/java/com/unitytunnel/app/MainActivity.kt', 'w') as f:
    f.write(content)
