local lib = require('CoronaLibrary'):new{name = 'plugin.gpgs.v3', publisherId = 'com.solar2d'}

local api = {
    achievements = {'load', 'increment', 'reveal', 'setSteps', 'unlock', 'show'},
    events = {'load', 'increment'},
    leaderboards = {'load', 'loadScores', 'submit', 'show'},
    players = {'load', 'loadStats', 'show', 'showCompare'},
    quests = {'load', 'accept', 'claim', 'show', 'showPopup', 'setListener', 'removeListener'},
    requests = {'load', 'accept', 'dismiss', 'getLimits', 'show', 'showSend', 'setListener', 'removeListener'},
    snapshots = {'load', 'open', 'save', 'discard', 'delete', 'resolveConflict', 'getSnapshot', 'getLimits', 'show'},
    videos = {'isSupported', 'isModeAvailable', 'loadCapabilities', 'getState', 'show', 'setListener', 'removeListener'},
    'enableDebug',
    'init',
    'isConnected',
    'isAuthenticated',
    'login',
    'logout',
    'getAppId',
    'getAccountName',
    'getServerAuthCode',
    'getSdkVersion',
    'setPopupPosition',
    'loadGame',
    'clearNotifications',
    'loadImage',
    'showSettings'
}

local function setStubs(t, node, path)
    for k, v in pairs(t) do
        if type(v) == 'string' then
            local notice = 'plugin.gpgs: ' .. table.concat(path, '.') .. (#path > 0 and '.' or '') .. v .. '() is not supported on this platform.'
            node[v] = function()
                print(notice)
            end
        elseif type(v) == 'table' then
            table.insert(path, k)
            node[k] = {}
            setStubs(v, node[k], path)
            table.remove(path, #path)
        end
    end
end

setStubs(api, lib, {})

return lib
