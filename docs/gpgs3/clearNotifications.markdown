# gpgs.v3.clearNotifications()

> --------------------- ------------------------------------------------------------------------------------------
> __Type__              [Function][api.type.Function]
> __Return value__      none
> __Revision__          [REVISION_LABEL](REVISION_URL)
> __Keywords__          Google Play Games Services, game network, gpgs
> __See also__          [gpgs3.*][plugin.gpgs.v3]
> --------------------- ------------------------------------------------------------------------------------------

## Overview

Clears notifications.

## Syntax

	gpgs.clearNotifications(notificationTypes)

##### notificationTypes ~^(optional)^~
_[Array][api.type.Array]._ Populate with [string][api.type.String] elements. List of notifications to be cleared. Accepted values: `"all"`, `"multiplayer"`, `"invitation"`, `"level up"`, `"match update"`, `"quest"`, `"request"`.
